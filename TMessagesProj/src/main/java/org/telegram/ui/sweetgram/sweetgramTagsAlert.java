package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.sweetgram.SweetgramTags;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

/**
 * Правка тегов у аудио прямо из чата: название, исполнитель, обложка.
 *
 * В телеграме это обычно делают через ботов — то есть отдают свой файл чужому
 * серверу ради трёх строчек текста. Здесь всё происходит на телефоне: теги
 * пишутся в копию файла, копия отправляется в тот же чат. Исходное сообщение
 * не трогаем: чужое сообщение править нельзя, а своё телеграм разрешает менять
 * только текстом, не файлом.
 *
 * Обложка выбирается телеграмовским выбором фотографий и кадрируется его же
 * экраном — тем самым, что режет аватарки. Системный выбор файлов, стоявший
 * здесь сначала, выглядел чужеродно, а обрезать в нём было нечем.
 */
public class SweetgramTagsAlert {

    /** Живой разбор переживает открытие выбора фото: экран уходит и приходит. */
    private static SweetgramTagsAlert current;

    private final ChatActivity fragment;
    private final MessageObject message;

    private String title = "";
    private String artist = "";
    private byte[] cover;
    private Bitmap coverPreview;

    private EditTextBoldCursor titleField;
    private EditTextBoldCursor artistField;
    private AlertDialog dialog;
    private ImageUpdater imageUpdater;
    private boolean coverTaken;
    private ImageView coverView;
    private TextView coverHint;

    private SweetgramTagsAlert(ChatActivity fragment, MessageObject message) {
        this.fragment = fragment;
        this.message = message;
        final TLRPC.Document document = message.getDocument();
        if (document != null) {
            for (TLRPC.DocumentAttribute a : document.attributes) {
                if (a instanceof TLRPC.TL_documentAttributeAudio) {
                    title = a.title == null ? "" : a.title;
                    artist = a.performer == null ? "" : a.performer;
                }
            }
        }
    }

    public static void show(ChatActivity fragment, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null || message == null) {
            return;
        }
        current = new SweetgramTagsAlert(fragment, message);
        current.open();
    }

    private static EditTextBoldCursor field(Activity context, CharSequence hint, String value) {
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setHintText(hint == null ? "" : hint.toString());
        edit.setBackgroundDrawable(null);
        edit.setLineColors(Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        edit.setSingleLine(true);
        edit.setPadding(0, dp(4), 0, dp(6));
        edit.setText(value);
        return edit;
    }

    private void open() {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }

        // Слева квадрат обложки, справа два поля. Так видно, что получится, —
        // а не только названия полей.
        coverView = new ImageView(context);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackgroundColor(Theme.getColor(Theme.key_dialogInputField));
        // Квадрат — это форма обложки альбома, а не моя лень; скругляю углы,
        // чтобы он не выглядел вырезанным ножницами.
        coverView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        coverView.setClipToOutline(true);
        coverView.setOnClickListener(v -> pickCover());

        coverHint = new TextView(context);
        coverHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        coverHint.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        coverHint.setGravity(Gravity.CENTER);
        coverHint.setOnClickListener(v -> pickCover());

        titleField = field(context, LocaleController.getString(R.string.SweetgramTrackTitle), title);
        artistField = field(context, LocaleController.getString(R.string.SweetgramTrackArtist), artist);

        final LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(titleField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
        fields.addView(artistField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 10, 0, 0));

        final LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(coverView, LayoutHelper.createLinear(76, 76, Gravity.TOP, 0, 6, 14, 0));
        top.addView(fields, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(coverHint, LayoutHelper.createLinear(76, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 6, 0, 0));

        updateCoverView();

        dialog = new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.SweetgramTrackTags))
                .setView(layout)
                .setPositiveButton(LocaleController.getString(R.string.SweetgramTrackSend), (d, w) -> apply())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void updateCoverView() {
        if (coverView == null) {
            return;
        }
        if (coverPreview != null) {
            coverView.setImageBitmap(coverPreview);
            coverHint.setText(LocaleController.getString(R.string.SweetgramTrackCoverChosen));
        } else {
            coverView.setImageResource(R.drawable.msg_round_file_s);
            coverView.setColorFilter(Theme.getColor(Theme.key_dialogTextHint));
            coverHint.setText(LocaleController.getString(R.string.SweetgramTrackCover));
        }
    }

    /** Запоминаем набранное: диалог сейчас закроется, а вернуться надо к нему же. */
    private void remember() {
        if (titleField != null) {
            title = titleField.getText().toString();
        }
        if (artistField != null) {
            artist = artistField.getText().toString();
        }
    }

    private void pickCover() {
        remember();
        // Окно надо закрыть ДО открытия галереи. Пока оно висело, галерея
        // открывалась за ним — владелец это увидел первым делом.
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        coverTaken = false;
        // Галерея и кадрирование телеграмовские, нынешние: тот же нижний лист,
        // что при смене аватарки. Своего кадрирования больше нет — из-за него
        // обрезка шла дважды, вторым разом чужим по стилю квадратным экраном.
        imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, false);
        imageUpdater.parentFragment = fragment;
        imageUpdater.setCanSelectVideo(false);
        // Обязательно версия с двумя доводами. Та, что с одним, кроме поиска
        // картинок выключает и сам нижний лист — а без него открывается старый
        // экран галереи. Ровно на это владелец и показал двумя снимками.
        imageUpdater.setSearchAvailable(false, true);
        // Ничего не загружаем: нам нужен файл на диске, а не аватарка на
        // сервере. Так честнее, чем отменять уже начатую отправку.
        imageUpdater.setUploadAfterSelect(false);
        imageUpdater.setDelegate((photo, video, videoStartTimestamp, videoPath, bigSize, smallSize, isVideo, emojiMarkup) -> {
            if (coverTaken || bigSize == null) {
                return;
            }
            coverTaken = true;
            final File file = FileLoader.getInstance(fragment.getCurrentAccount())
                    .getPathToAttach(bigSize, true);
            if (file != null && file.exists()) {
                takeCover(BitmapFactory.decodeFile(file.getAbsolutePath()));
            }
            AndroidUtilities.runOnUIThread(this::open, 120);
        });
        imageUpdater.openMenu(false, null, d -> {
        }, 0);
    }

    private void takeCover(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        // Обложку ужимаем: в тег иногда кладут снимок с камеры на пять
        // мегабайт, и он поедет вместе с песней каждому получателю.
        final int max = 800;
        Bitmap out = bitmap;
        if (out.getWidth() > max || out.getHeight() > max) {
            final float scale = Math.min(max / (float) out.getWidth(), max / (float) out.getHeight());
            out = Bitmap.createScaledBitmap(out,
                    Math.round(out.getWidth() * scale), Math.round(out.getHeight() * scale), true);
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 87, bytes);
        cover = bytes.toByteArray();
        coverPreview = out;
    }

    private void apply() {
        remember();
        final File src = FileLoader.getInstance(fragment.getCurrentAccount())
                .getPathToMessage(message.messageOwner);
        if (src == null || !src.exists()) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.SweetgramTrackNeedFile)).show();
            return;
        }
        final String name = message.getDocumentName();
        final File dst = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                (name == null || name.isEmpty() ? "track.mp3" : name));
        if (!SweetgramTags.write(src, dst, title.trim(), artist.trim(), cover)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.SweetgramTrackWriteFailed)).show();
            return;
        }
        SendMessagesHelper.prepareSendingDocument(fragment.getAccountInstance(),
                dst.getAbsolutePath(), dst.getAbsolutePath(), null, null, "audio/mpeg",
                fragment.getDialogId(), null, fragment.getThreadMessage(), null, null, null,
                true, 0, null, fragment.getMessageChatSendParams(), false);
        current = null;
    }
}
