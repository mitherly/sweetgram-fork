package org.telegram.sweetgram;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Удалённые подарки: телеграм убрал их из каталога, но сами подарки никуда не
 * делись — сервер по-прежнему принимает отправку по номеру.
 *
 * Как это устроено. Запрос каталога уходит с hash=0, иначе сервер отвечает
 * «ничего не изменилось» и списка не присылает вовсе. В полученный список мы
 * дописываем подарки из открытого перечня, а картинки к ним берём из
 * стикерпака.
 *
 * Придумал это не я. Приём и сам перечень — из плагина «Deleted Gift Sender»
 * автора @binbash_0; здесь всё написано заново на джаве, но идея его, и
 * ссылка на него стоит прямо в настройках.
 *
 * Покупку в любом случае подтверждает сервер: если подарок закрыт по-настоящему,
 * отправка не пройдёт, и это будет не ошибка форка.
 */
public class SweetgramGifts {

    public static final String AUTHOR = "binbash_0";
    private static final String LIST_URL =
            "https://raw.githubusercontent.com/binbash-0/DeletedGifts-Plugin/refs/heads/main/gift_list.json";

    private static class Entry {
        long id;
        long price;
        int stickerNumber;
        String name;
    }

    private static final ArrayList<Entry> entries = new ArrayList<>();
    private static final ArrayList<TLRPC.Document> stickers = new ArrayList<>();
    private static String stickerPack = "DeletedGiftsStickers";
    private static boolean loadingList;
    private static boolean loadingStickers;

    public static boolean enabled() {
        return SweetgramConfig.giftsEnabled();
    }

    /** Сколько подарков сейчас знаем — для строчки в настройках. */
    public static int known() {
        return entries.size();
    }

    public static void load(int account) {
        if (!enabled()) {
            return;
        }
        loadList(account);
    }

    private static void loadList(int account) {
        if (loadingList || !entries.isEmpty()) {
            loadStickers(account);
            return;
        }
        loadingList = true;
        Utilities.globalQueue.postRunnable(() -> {
            String json = null;
            try {
                final HttpURLConnection connection = (HttpURLConnection) new URL(LIST_URL).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                try (InputStream in = connection.getInputStream()) {
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    final byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        out.write(buf, 0, read);
                    }
                    json = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                // Без списка просто не будет лишних подарков. Не повод шуметь.
                FileLog.e(e);
            }
            final String result = json;
            AndroidUtilities.runOnUIThread(() -> {
                loadingList = false;
                parse(result);
                loadStickers(account);
            });
        });
    }

    private static void parse(String json) {
        if (json == null) {
            return;
        }
        try {
            final JSONObject root = new JSONObject(json);
            stickerPack = root.optString("stickerpack", stickerPack);
            final JSONArray list = root.optJSONArray("gifts");
            entries.clear();
            for (int i = 0; list != null && i < list.length(); i++) {
                final JSONObject item = list.getJSONObject(i);
                final Entry entry = new Entry();
                entry.id = item.getLong("id");
                entry.price = item.optLong("price", 0);
                entry.stickerNumber = item.optInt("sticker_number", 0);
                entry.name = item.optString("debug_name", "");
                entries.add(entry);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void loadStickers(int account) {
        if (loadingStickers || !stickers.isEmpty() || entries.isEmpty()) {
            return;
        }
        loadingStickers = true;
        final TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        final TLRPC.TL_inputStickerSetShortName set = new TLRPC.TL_inputStickerSetShortName();
        set.short_name = stickerPack;
        req.stickerset = set;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loadingStickers = false;
            if (res instanceof TLRPC.TL_messages_stickerSet) {
                stickers.clear();
                stickers.addAll(((TLRPC.TL_messages_stickerSet) res).documents);
            }
        }));
    }

    /**
     * Дописывает удалённые подарки в конец каталога. Донор нужен ровно за одним:
     * взять у него картинку, если стикерпак ещё не пришёл. Без картинки подарок
     * не нарисуется вовсе.
     */
    public static void inject(int account, ArrayList<TL_stars.StarGift> gifts) {
        if (!enabled() || gifts == null || gifts.isEmpty()) {
            return;
        }
        load(account);
        if (entries.isEmpty()) {
            return;
        }
        TLRPC.Document donor = null;
        for (int i = 0; i < gifts.size(); i++) {
            if (gifts.get(i).sticker != null) {
                donor = gifts.get(i).sticker;
                break;
            }
        }
        if (donor == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            final Entry entry = entries.get(i);
            boolean already = false;
            for (int j = 0; j < gifts.size(); j++) {
                if (gifts.get(j).id == entry.id) {
                    already = true;
                    break;
                }
            }
            if (already) {
                continue;
            }
            final TL_stars.TL_starGift gift = new TL_stars.TL_starGift();
            gift.id = entry.id;
            gift.stars = entry.price;
            gift.convert_stars = entry.price;
            final int index = entry.stickerNumber - 1;
            gift.sticker = index >= 0 && index < stickers.size() ? stickers.get(index) : donor;
            gifts.add(gift);
        }
    }
}
