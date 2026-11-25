package com.hai.sellaxe.modules;

import com.hai.sellaxe.SellAxeAddon;
import com.hai.sellaxe.util.Money;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPayModule extends Module {
    private enum State { IDLE, STOPPING, WAIT_BAL, PAYING, RESTART_WAIT }

    private static final int RETRY_TICKS = 2 * 60 * 20; // thử lại sau 2 phút nếu lỗi

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Nâng cao");

    // ---------- Chung ----------
    private final Setting<String> username = sgGeneral.add(new StringSetting.Builder()
        .name("tên-người-nhận")
        .description("Tên người chơi nhận tiền.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> intervalMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("chu-kỳ-pay-phút")
        .description("Cứ bao nhiêu phút thì pay một lần.")
        .defaultValue(60)
        .min(1)
        .sliderMax(240)
        .build()
    );

    private final Setting<Double> taxPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("trừ-thuế-phần-trăm")
        .description("Số phần trăm trừ đi trước khi /pay để chừa chỗ cho thuế (nên để 3 đến 5).")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> minBalance = sgGeneral.add(new DoubleSetting.Builder()
        .name("số-dư-tối-thiểu")
        .description("Số dư phải lớn hơn mức này thì mới pay.")
        .defaultValue(1000)
        .min(0)
        .sliderMax(1000000)
        .build()
    );

    // ---------- Nâng cao ----------
    private final Setting<Integer> stopDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("tắt-sellaxe-trước-khi-bal-giây")
        .description("Tắt SellAxe bao nhiêu giây rồi mới gõ /bal.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> resumeDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("bật-lại-sellaxe-sau-pay-giây")
        .description("Sau khi /pay bao nhiêu giây thì bật lại SellAxe.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> failsafeSeconds = sgAdvanced.add(new IntSetting.Builder()
        .name("tự-bật-lại-sellaxe-sau-giây")
        .description("Nếu SellAxe bị AutoPay tắt quá số giây này mà chưa pay xong thì tự bật lại.")
        .defaultValue(60)
        .min(15)
        .sliderMax(300)
        .build()
    );

    private final Setting<Integer> restartWait = sgAdvanced.add(new IntSetting.Builder()
        .name("chờ-khi-restart-region-giây")
        .description("Khi thấy cảnh báo restart region giữa lúc pay, chờ bao nhiêu giây rồi bật lại SellAxe.")
        .defaultValue(30)
        .min(5)
        .sliderMax(120)
        .build()
    );

    private final Setting<String> balanceRegex = sgAdvanced.add(new StringSetting.Builder()
        .name("regex-đọc-số-dư")
        .description("Regex đọc dòng chat trả về của /bal. Nhóm số 1 phải là số tiền.")
        .defaultValue("(?i)(?:balance|bal|money|coins)\\D{0,20}\\$?\\s?([\\d,]+(?:\\.\\d+)?[kmbt]?)(?![a-z])")
        .build()
    );

    private final Setting<Boolean> debug = sgAdvanced.add(new BoolSetting.Builder()
        .name("gỡ-lỗi-chat")
        .description("In ra mọi dòng chat nhận được khi đang chờ /bal (để chỉnh regex).")
        .defaultValue(false)
        .build()
    );

    private State state = State.IDLE;
    private int timer = 0;
    private int cycleTicks = -1;
    private int stoppedTicks = 0;
    private boolean stoppedByUs = false;
    private String lastRegex = null;
    private Pattern lastPattern = null;

    public AutoPayModule() {
        super(SellAxeAddon.CATEGORY, "AutoPay", "Tự /pay toàn bộ tiền (đã trừ thuế) cho một người chơi theo chu kỳ, tạm dừng SellAxe khi pay.");
    }

    @Override
    public void onActivate() {
        if (cycleTicks < 0) cycleTicks = intervalMinutes.get() * 60 * 20;
    }

    @Override
    public void onDeactivate() {
        resumeSellAxe(false);
        state = State.IDLE;
    }

    private Pattern balancePattern() {
        String r = balanceRegex.get();
        if (!r.equals(lastRegex)) {
            try {
                lastPattern = Pattern.compile(r);
            } catch (Exception e) {
                warning("Regex đọc số dư không hợp lệ.");
                lastPattern = Pattern.compile("a^");
            }
            lastRegex = r;
        }
        return lastPattern;
    }

    private SellAxeModule sellAxe() {
        return Modules.get().get(SellAxeModule.class);
    }

    // skipHome = true khi nhân vật vẫn đứng đúng chỗ (pay xong bình thường) -> SellAxe không cần /home
    private void resumeSellAxe(boolean skipHome) {
        if (!stoppedByUs) return;
        stoppedByUs = false;
        stoppedTicks = 0;
        SellAxeModule sa = sellAxe();
        if (sa != null && !sa.isActive()) {
            if (skipHome) sa.skipHomeNextStart();
            sa.toggle();
        }
    }

    private void abort(int nextCycleTicks, boolean skipHome) {
        resumeSellAxe(skipHome);
        state = State.IDLE;
        cycleTicks = nextCycleTicks;
    }

    private void beginPay() {
        if (username.get().isBlank()) {
            warning("Chưa nhập tên người nhận cho AutoPay.");
            cycleTicks = intervalMinutes.get() * 60 * 20;
            return;
        }
        SellAxeModule sa = sellAxe();
        if (sa != null && sa.isActive()) {
            sa.toggle();
            stoppedByUs = true;
        }
        stoppedTicks = 0;
        state = State.STOPPING;
        timer = stopDelay.get() * 20;
        info("Đến chu kỳ pay, tạm dừng SellAxe.");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        // Vào lại server giữa chừng -> huỷ lượt pay đang dở và bật lại SellAxe
        if (state != State.IDLE || stoppedByUs) abort(RETRY_TICKS, false);
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        String low = msg.toLowerCase();

        if (low.contains("server are updating") || low.contains("server is updating")) {
            if (state != State.IDLE || stoppedByUs) {
                state = State.RESTART_WAIT;
                timer = restartWait.get() * 20;
            }
            return;
        }

        if (state != State.WAIT_BAL) return;
        if (debug.get()) info("[chat] %s", msg);

        try {
            Matcher m = balancePattern().matcher(msg);
            if (!m.find()) return;

            double balance = Money.parse(m.group(1));
            if (balance < minBalance.get()) {
                info("Số dư " + Money.format(balance) + " quá thấp, bỏ qua lượt này.");
                abort(intervalMinutes.get() * 60 * 20, true);
                return;
            }

            long pay = (long) Math.floor(balance * (1.0 - taxPercent.get() / 100.0));
            ChatUtils.sendPlayerMsg("/pay " + username.get() + " " + pay);
            info("Pay " + username.get() + " " + Money.format(pay) + " (số dư " + Money.format(balance) + ").");

            state = State.PAYING;
            timer = resumeDelay.get() * 20;
        } catch (Exception e) {
            warning("Không đọc được số dư: %s", String.valueOf(e.getMessage()));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Chốt an toàn: SellAxe bị mình tắt quá lâu -> bật lại
        if (stoppedByUs && ++stoppedTicks >= failsafeSeconds.get() * 20) {
            warning("Quá thời gian chờ, tự bật lại SellAxe.");
            abort(RETRY_TICKS, false);
            return;
        }

        int max = intervalMinutes.get() * 60 * 20;
        if (cycleTicks > max) cycleTicks = max;

        switch (state) {
            case IDLE -> {
                if (--cycleTicks <= 0) beginPay();
            }
            case STOPPING -> {
                if (--timer <= 0) {
                    ChatUtils.sendPlayerMsg("/bal");
                    state = State.WAIT_BAL;
                    timer = 10 * 20;
                }
            }
            case WAIT_BAL -> {
                if (--timer <= 0) {
                    warning("Không nhận được số dư từ /bal, bật lại SellAxe.");
                    abort(RETRY_TICKS, true);
                }
            }
            case PAYING -> {
                if (--timer <= 0) abort(max, true);
            }
            case RESTART_WAIT -> {
                if (--timer <= 0) abort(RETRY_TICKS, false);
            }
        }
    }
}
