package org.telegram.ui;

import android.os.Bundle;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.sweetgram.SweetgramWallGroup;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/**
 * Стена: что о человеке написали другие.
 *
 * Дуров стену убрал, здесь она возвращается — и не как страница, которую
 * хозяин правит под себя. Смысл ровно в обратном: написанное про тебя ты
 * снять не можешь. Поэтому стена и работает против разводил — обманутый
 * пишет, обманщик не стирает, а видят все.
 *
 * Это обычный экран переписки, у которого убрано всё, кроме сообщений с
 * меткой этой стены. Отбор стоит внутри {@link ChatActivity}, на входе списка
 * сообщений; всё остальное — список, поле ввода, меню сообщения, реакции —
 * работает ровно так же, как в любой переписке.
 *
 * Порт из Margy (Margelet); хранилище записей — отдельная группа, см.
 * {@link SweetgramWallGroup}.
 */
public class SweetgramWallActivity extends ChatActivity {

    /**
     * Сколько сообщений стены забираем поиском.
     *
     * Сотня — предел одного запроса к серверу. Стены длиннее сотни сообщений
     * пока не встречалось; появятся — доберём страницами, но выдумывать это
     * заранее незачем.
     */
    private static final int WALL_LIMIT = 100;

    private final long peerId;
    private final String peerName;

    private SweetgramWallActivity(Bundle args, long peerId, String peerName) {
        super(args);
        this.peerId = peerId;
        this.peerName = peerName == null ? "" : peerName;
    }

    /**
     * Открыть чью-то стену.
     *
     * Через статический метод, а не через конструктор: адрес группы сперва
     * надо выяснить, а это поездка на сервер. Конструктор, умеющий ждать,
     * обманывает вызывающего — он вернёт экран, который ещё не знает, что
     * показывать.
     */
    public static void open(BaseFragment from, long peerId, String peerName) {
        if (from == null || peerId == 0) {
            return;
        }
        SweetgramWallGroup.resolve(dialogId -> {
            if (dialogId == 0) {
                BulletinFactory.of(from).createSimpleBulletin(R.raw.error,
                        "Не удалось открыть общую группу стены").show();
                return;
            }
            final Bundle args = new Bundle();
            // Группа приходит номером переписки — со знаком минус и приставкой
            // канала; ChatActivity ждёт голый номер чата.
            args.putLong("chat_id", -dialogId);
            args.putString("sweetgramWallTag", SweetgramWallGroup.tagWall(peerId));
            args.putString("sweetgramWallName", peerName == null ? "" : peerName);
            args.putLong("sweetgramWallPeer", peerId);
            final SweetgramWallActivity wall = new SweetgramWallActivity(args, peerId, peerName);
            // Сообщения стены спрашиваем у сервера ДО открытия экрана, а не
            // после. Иначе экран открывается пустым и ждёт, пока обычная
            // догрузка переписки доберётся до нужных сообщений, — а она не
            // доберётся: из принесённой полусотни с меткой остаётся одно, и
            // листать телеграму нечего. Поиск по метке знает всю группу и
            // отвечает сразу.
            SweetgramWallGroup.find(SweetgramWallGroup.tagWall(peerId), 0, WALL_LIMIT,
                    (found, problem) -> AndroidUtilities.runOnUIThread(() -> {
                        // Отдаём найденное, только если спросить удалось.
                        // Пустой ответ от неответившего сервера значит «не
                        // знаю», а не «сообщений нет», и принять его за второе
                        // — значит показать пустую стену и объявить, что это
                        // всё.
                        if (problem == null) {
                            wall.sweetgramFound(found);
                        }
                        from.presentFragment(wall);
                    }));
        });
    }

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) {
            return false;
        }
        // Пока экран открыт, отправка дописывает метку сама. Человек пишет в
        // обычное поле обычной переписки и про метки ничего не знает — знать
        // ему и незачем, это наша служебная разметка, а не его забота.
        SweetgramWallGroup.writingTo(peerId);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        SweetgramWallGroup.writingTo(peerId);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Ушли с экрана — метку больше не дописываем. Иначе она уехала бы в
        // соседнюю переписку вместе со следующим же сообщением.
        SweetgramWallGroup.writingTo(0);
    }

    @Override
    public void onFragmentDestroy() {
        SweetgramWallGroup.writingTo(0);
        super.onFragmentDestroy();
    }
}
