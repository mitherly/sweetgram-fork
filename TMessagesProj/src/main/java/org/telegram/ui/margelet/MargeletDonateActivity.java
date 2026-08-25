package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Донат»: куда можно отправить деньги автору форка.
 *
 * Кнопок оплаты внутри приложения нет намеренно. Форк мессенджера — последнее
 * место, где стоит вводить платёжные данные, и просить об этом человека я не
 * буду. Здесь только реквизиты, которые копируются нажатием; платит человек
 * там, где обычно платит.
 *
 * Номер разбит по четыре цифры для глаза, а копируется сплошным: пробелы в
 * поле перевода мешают.
 */
public class MargeletDonateActivity extends UniversalFragment {

    private static final int ID_YOOMONEY = 1;
    private static final int ID_ROBLOX = 2;
    private static final int ID_GIFT = 3;
    private static final int ID_PAGE = 4;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletDonate);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    private static String spaced(String digits) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_YOOMONEY, LocaleController.getString(R.string.MargeletDonateYoomoney),
                spaced(MargeletConfig.DONATE_YOOMONEY)));
        items.add(UItem.asButton(ID_ROBLOX, LocaleController.getString(R.string.MargeletDonateRoblox),
                MargeletConfig.DONATE_ROBLOX));
        items.add(UItem.asButton(ID_GIFT, LocaleController.getString(R.string.MargeletDonateGift),
                "@" + MargeletConfig.DONATE_GIFT_USERNAME));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletDonateAbout)));
        // Страница открывается, а не копируется: там готовая форма перевода.
        items.add(UItem.asButton(ID_PAGE, LocaleController.getString(R.string.MargeletDonatePage)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletDonatePageAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_GIFT) {
            openGift();
            return;
        }
        if (item.id == ID_PAGE) {
            Browser.openUrl(getContext(), MargeletConfig.DONATE_PAGE);
            return;
        }
        final String value = item.id == ID_YOOMONEY ? MargeletConfig.DONATE_YOOMONEY
                : item.id == ID_ROBLOX ? MargeletConfig.DONATE_ROBLOX : null;
        if (value == null) {
            return;
        }
        AndroidUtilities.addToClipboard(value);
        BulletinFactory.of(this).createCopyBulletin(
                LocaleController.getString(R.string.MargeletDonateCopied)).show();
    }

    /**
     * Открывает окно покупки подарка за звёзды.
     *
     * Номер известен заранее, но человека может не быть в памяти приложения —
     * тогда окну нечего показывать. Поэтому если его нет, сначала спрашиваем
     * сервер по нику, и только потом открываем. Не вышло и это — открываем
     * переписку, дальше человек справится сам.
     */
    private void openGift() {
        if (getContext() == null) {
            return;
        }
        if (getMessagesController().getUser(MargeletConfig.DONATE_GIFT_USER) != null) {
            showDialog(new GiftSheet(getContext(), currentAccount, MargeletConfig.DONATE_GIFT_USER, null, null));
            return;
        }
        getMessagesController().getUserNameResolver().resolve(MargeletConfig.DONATE_GIFT_USERNAME, id -> {
            if (getContext() == null) {
                return;
            }
            if (id == null || id <= 0) {
                Browser.openUrl(getContext(), "https://t.me/" + MargeletConfig.DONATE_GIFT_USERNAME);
                return;
            }
            showDialog(new GiftSheet(getContext(), currentAccount, id, null, null));
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
