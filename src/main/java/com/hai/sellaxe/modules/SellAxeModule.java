package com.hai.sellaxe.modules;

import com.hai.sellaxe.SellAxeAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.regex.Pattern;

public class SellAxeModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDeath = settings.createGroup("Khi cháº¿t");
    private final SettingGroup sgRestart = settings.createGroup("Khi restart region");
    private final SettingGroup sgIdle = settings.createGroup("Chá»ng Äá»©ng yÃªn");

    // ---------- Chung ----------
    private final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("giá»¯-sneak")
        .description("Giá»¯ phÃ­m sneak á» bÆ°á»c Äáº§u tiÃªn.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stepDelay = sgGeneral.add(new IntSetting.Builder()
        .name("trá»-má»i-bÆ°á»c-tick")
        .description("Khoáº£ng cÃ¡ch giá»¯a cÃ¡c bÆ°á»c: nhÃ¬n lÃªn, giá»¯ chuá»t pháº£i, giá»¯ chuá»t trÃ¡i (10 tick = 0,5 giÃ¢y).")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> reconnectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chá»-sau-khi-vÃ o-láº¡i-giÃ¢y")
        .description("Sau khi vÃ o láº¡i server (hoáº·c háº¿t restart region) táº¯t bao nhiÃªu giÃ¢y rá»i má»i cháº¡y láº¡i.")
        .defaultValue(5)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> homeOnStart = sgGeneral.add(new BoolSetting.Builder()
        .name("tá»±-home-khi-báº­t")
        .description("Má»i láº§n báº­t SellAxe (hoáº·c cháº¡y láº¡i sau khi vÃ o láº¡i server) sáº½ tá»± gÃµ /home trÆ°á»c khi báº¯t Äáº§u.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> homeName = sgGeneral.add(new StringSetting.Builder()
        .name("tÃªn-home")
        .description("TÃªn home dÃ¹ng cho má»i lá»nh /home cá»§a SellAxe (vÃ­ dá»¥: 1).")
        .defaultValue("1")
        .build()
    );

    private final Setting<Integer> homeWait = sgGeneral.add(new IntSetting.Builder()
        .name("chá»-sau-home-giÃ¢y")
        .description("Sau khi gÃµ /home, chá» bao nhiÃªu giÃ¢y cho dá»ch chuyá»n xong rá»i má»i cháº¡y.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> workInFreecam = sgGeneral.add(new BoolSetting.Builder()
        .name("cháº¡y-khi-báº­t-freecam")
        .description("Khi báº­t Freecam cá»§a Meteor, SellAxe váº«n cháº·t/Än theo hÆ°á»ng nhÃ¬n cá»§a nhÃ¢n váº­t (khÃ´ng theo camera).")
        .defaultValue(true)
        .build()
    );

    // ---------- Khi cháº¿t ----------
    private final Setting<Boolean> deathHandling = sgDeath.add(new BoolSetting.Builder()
        .name("tá»±-xá»­-lÃ½-khi-cháº¿t")
        .description("Khi nhÃ¢n váº­t cháº¿t (cáº§n báº­t AutoRespawn cá»§a Meteor): tá»± /home rá»i táº¯t vÃ  báº­t láº¡i SellAxe.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> deathHomeDelay = sgDeath.add(new IntSetting.Builder()
        .name("chá»-home-sau-khi-cháº¿t-giÃ¢y")
        .description("Sau khi cháº¿t bao nhiÃªu giÃ¢y thÃ¬ gÃµ /home (tÃ­nh tá»« lÃºc cháº¿t).")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> deathRestartDelay = sgDeath.add(new IntSetting.Builder()
        .name("báº­t-láº¡i-sau-home-giÃ¢y")
        .description("Sau khi gÃµ /home, SellAxe táº¯t vÃ  báº­t láº¡i sau bao nhiÃªu giÃ¢y.")
        .defaultValue(3)
        .min(1)
        .sliderMax(30)
        .build()
    );

    // ---------- Khi restart region ----------
    private final Setting<String> restartBarRegex = sgRestart.add(new StringSetting.Builder()
        .name("regex-dÃ²ng-chá»¯-hotbar")
        .description("Regex nháº­n dÃ²ng chá»¯ trÃªn thanh hotbar khi server Äang restart hoáº·c xáº¿p hÃ ng.")
        .defaultValue("(?i)(restart|updat|queue|position|slot|region|server)")
        .build()
    );

    private final Setting<Integer> barGoneSeconds = sgRestart.add(new IntSetting.Builder()
        .name("hotbar-máº¥t-chá»¯-giÃ¢y")
        .description("DÃ²ng chá»¯ trÃªn hotbar biáº¿n máº¥t liÃªn tá»¥c bao nhiÃªu giÃ¢y thÃ¬ coi lÃ  restart xong.")
        .defaultValue(3)
        .min(1)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> restartFallback = sgRestart.add(new IntSetting.Builder()
        .name("chá»-dá»±-phÃ²ng-giÃ¢y")
        .description("Náº¿u khÃ´ng tháº¥y dÃ²ng chá»¯ nÃ o trÃªn hotbar thÃ¬ chá» bao nhiÃªu giÃ¢y rá»i cháº¡y láº¡i.")
        .defaultValue(30)
        .min(5)
        .sliderMax(120)
        .build()
    );

    private final Setting<Integer> restartMaxWait = sgRestart.add(new IntSetting.Builder()
        .name("chá»-tá»i-Äa-giÃ¢y")
        .description("Chá» tá»i Äa bao nhiÃªu giÃ¢y (phÃ²ng khi dÃ²ng chá»¯ khÃ´ng bao giá» máº¥t).")
        .defaultValue(600)
        .min(30)
        .sliderMax(1800)
        .build()
    );

    private final Setting<Boolean> debug = sgRestart.add(new BoolSetting.Builder()
        .name("gá»¡-lá»i-hotbar")
        .description("In ra cÃ¡c dÃ²ng chá»¯ trÃªn hotbar nháº­n ÄÆ°á»£c trong lÃºc restart (Äá» chá»nh regex).")
        .defaultValue(false)
        .build()
    );

    // ---------- Chá»ng Äá»©ng yÃªn ----------
    private final Setting<Boolean> autoHome = sgIdle.add(new BoolSetting.Builder()
        .name("tá»±-home-khi-Äá»©ng-yÃªn")
        .description("Äá»©ng yÃªn quÃ¡ lÃ¢u thÃ¬ tá»± /home.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> idleSeconds = sgIdle.add(new IntSetting.Builder()
        .name("Äá»©ng-yÃªn-bao-lÃ¢u-giÃ¢y")
        .description("Äá»©ng yÃªn bao nhiÃªu giÃ¢y thÃ¬ /home.")
        .defaultValue(120)
        .min(10)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> noChestHome = sgIdle.add(new BoolSetting.Builder()
        .name("tá»±-home-khi-khÃ´ng-cháº·t-rÆ°Æ¡ng")
        .description("QuÃ¡ lÃ¢u khÃ´ng cháº·t ÄÆ°á»£c rÆ°Æ¡ng thÃ¬ tá»± /home.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> noChestSeconds = sgIdle.add(new IntSetting.Builder()
        .name("khÃ´ng-cháº·t-rÆ°Æ¡ng-bao-lÃ¢u-giÃ¢y")
        .description("Bao nhiÃªu giÃ¢y khÃ´ng cháº·t rÆ°Æ¡ng thÃ¬ /home.")
        .defaultValue(120)
        .min(10)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> anyBlock = sgIdle.add(new BoolSetting.Builder()
        .name("tÃ­nh-cáº£-block-khÃ¡c")
        .description("TÃ­nh cáº£ block khÃ¡c (barrel, shulker...) lÃ  Äang cháº·t, khÃ´ng chá» rÆ°Æ¡ng.")
        .defaultValue(false)
        .build()
    );

    // 0 = chá» (hoáº·c chá» /home), 1 = khoÃ¡ nhÃ¬n lÃªn (+sneak), 2 = giá»¯ chuá»t pháº£i, 3 = giá»¯ cáº£ chuá»t trÃ¡i
    private int stage = 0;
    private int timer = 0;
    private boolean pendingHome = false;
    private boolean skipHomeOnce = false;

    private volatile int tick = 0;
    private volatile boolean restartMode = false;
    private volatile boolean barSeen = false;
    private volatile int lastBarTick = 0;
    private int restartTicks = 0;

    // Xá»­ lÃ½ khi cháº¿t
    private boolean deathMode = false;
    private boolean wasDead = false;
    private boolean homeSentAfterDeath = false;
    private int deathTicks = 0;
    private int postHomeTicks = 0;

    private Object lastWorld = null;
    private Vec3d idleAnchor = null;
    private int idleTicks = 0;
    private int noChestTicks = 0;
    private boolean breakingChest = false;

    // Äá»ng bá» camera Freecam vá»i nhÃ¢n váº­t trong lÃºc game xá»­ lÃ½ tick
    private Freecam syncedFreecam = null;
    private double savedX, savedY, savedZ;
    private float savedYaw, savedPitch;

    private String lastRegex = null;
    private Pattern lastPattern = null;

    public SellAxeModule() {
        super(SellAxeAddon.CATEGORY, "SellAxe", "SellAxe by Háº£i Tráº§n");
    }

    /** AutoPay gá»i trÆ°á»c khi báº­t láº¡i SellAxe Äá» khÃ´ng /home thá»«a (Äang Äá»©ng ÄÃºng chá» rá»i). */
    public void skipHomeNextStart() {
        skipHomeOnce = true;
    }

    @Override
    public void onActivate() {
        lastWorld = mc.world;
        restartMode = false;
        deathMode = false;
        wasDead = false;

        boolean justJoined = mc.player == null || mc.player.age < 40;
        boolean goHome = !skipHomeOnce;
        skipHomeOnce = false;
        start(justJoined ? reconnectDelay.get() * 20 : 0, goHome);
    }

    @Override
    public void onDeactivate() {
        restoreFreecam();
        releaseKeys();
        stage = 0;
        pendingHome = false;
        restartMode = false;
        deathMode = false;
    }

    private void start(int delayTicks, boolean goHome) {
        releaseKeys();
        stage = 0;
        timer = delayTicks;
        pendingHome = goHome && homeOnStart.get();
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
    }

    private void releaseKeys() {
        mc.options.sneakKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    private void sendHomeCommand() {
        String name = homeName.get().trim();
        ChatUtils.sendPlayerMsg(name.isEmpty() ? "/home" : "/home " + name);
    }

    private Pattern barPattern() {
        String r = restartBarRegex.get();
        if (!r.equals(lastRegex)) {
            try {
                lastPattern = Pattern.compile(r);
            } catch (Exception e) {
                warning("Regex dÃ²ng chá»¯ hotbar khÃ´ng há»£p lá».");
                lastPattern = Pattern.compile("a^");
            }
            lastRegex = r;
        }
        return lastPattern;
    }

    private void enterRestart() {
        if (restartMode || deathMode) return;
        releaseKeys();
        stage = 0;
        timer = 0;
        pendingHome = false;
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
        restartTicks = 0;
        barSeen = false;
        lastBarTick = tick;
        restartMode = true;
        info("PhÃ¡t hiá»n restart region, táº¡m dá»«ng SellAxe.");
    }

    private void enterDeath() {
        releaseKeys();
        stage = 0;
        timer = 0;
        pendingHome = false;
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
        restartMode = false;
        deathMode = true;
        deathTicks = 0;
        postHomeTicks = 0;
        homeSentAfterDeath = false;
        info("NhÃ¢n váº­t ÄÃ£ cháº¿t, táº¡m dá»«ng SellAxe.");
    }

    // ---------- Sá»± kiá»n ----------

    // VÃ o láº¡i server: táº¯t (nháº£ phÃ­m), chá» rá»i /home vÃ  cháº¡y láº¡i
    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        restartMode = false;
        deathMode = false;
        wasDead = false;
        lastWorld = mc.world;
        start(reconnectDelay.get() * 20, true);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        releaseKeys();
        stage = 0;
        pendingHome = false;
        restartMode = false;
        deathMode = false;
        lastWorld = null;
    }

    // DÃ²ng cáº£nh bÃ¡o restart region trong chat
    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase();
        if (msg.contains("server are updating") || msg.contains("server is updating")) {
            enterRestart();
        }
    }

    // DÃ²ng chá»¯ trÃªn thanh hotbar (action bar)
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!restartMode) return;

        Text text = null;
        if (event.packet instanceof OverlayMessageS2CPacket p) text = p.text();
        else if (event.packet instanceof GameMessageS2CPacket p && p.overlay()) text = p.content();
        if (text == null) return;

        String s = text.getString();
        if (debug.get()) mc.execute(() -> info("[hotbar] %s", s));

        if (barPattern().matcher(s).find()) {
            barSeen = true;
            lastBarTick = tick;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        if (mc.player == null || mc.world == null) return;

        // ----- Cháº¿t: /home sau 5 giÃ¢y rá»i táº¯t/báº­t láº¡i SellAxe -----
        boolean dead = mc.player.isDead() || mc.currentScreen instanceof DeathScreen;
        if (deathHandling.get()) {
            if (dead && !wasDead) enterDeath();
            wasDead = dead;

            if (deathMode) {
                handleDeath(dead);
                return;
            }
        } else {
            wasDead = dead;
            if (deathMode) {
                deathMode = false;
                start(0, false);
            }
        }

        // Äá»i world mÃ  khÃ´ng cÃ³ sá»± kiá»n join (bá» chuyá»n region) -> táº¯t rá»i cháº¡y láº¡i
        if (mc.world != lastWorld) {
            boolean first = lastWorld == null;
            lastWorld = mc.world;
            idleAnchor = null;
            idleTicks = 0;
            if (!first && !restartMode) start(reconnectDelay.get() * 20, true);
        }

        // Äang chá» restart region: Äá»£i dÃ²ng chá»¯ trÃªn hotbar biáº¿n máº¥t
        if (restartMode) {
            restartTicks++;
            boolean barGone = barSeen && (tick - lastBarTick) >= barGoneSeconds.get() * 20;
            boolean noBar = !barSeen && restartTicks >= restartFallback.get() * 20;
            boolean tooLong = restartTicks >= restartMaxWait.get() * 20;
            if (barGone || noBar || tooLong) {
                restartMode = false;
                info("Restart xong, SellAxe sáº½ cháº¡y láº¡i.");
                start(reconnectDelay.get() * 20, true);
            }
            return;
        }

        if (stage >= 1) mc.player.setPitch(-90f);

        if (timer > 0) {
            timer--;
            return;
        }

        switch (stage) {
            case 0 -> {
                if (pendingHome) {
                    pendingHome = false;
                    sendHomeCommand();
                    info("Tá»± /home trÆ°á»c khi cháº¡y.");
                    timer = homeWait.get() * 20;
                } else {
                    mc.player.setPitch(-90f);
                    if (sneak.get()) mc.options.sneakKey.setPressed(true);
                    stage = 1;
                    timer = stepDelay.get();
                }
            }
            case 1 -> {
                mc.options.useKey.setPressed(true);
                stage = 2;
                timer = stepDelay.get();
            }
            case 2 -> {
                mc.options.attackKey.setPressed(true);
                stage = 3;
            }
            case 3 -> {
                if (sneak.get()) mc.options.sneakKey.setPressed(true);
                mc.options.useKey.setPressed(true);
                mc.options.attackKey.setPressed(true);
                checkIdle();
                checkChest();
            }
        }
    }

    // Sau khi cháº¿t: Äá»§ thá»i gian + ÄÃ£ há»i sinh -> /home; sau ÄÃ³ chá» rá»i cháº¡y láº¡i
    private void handleDeath(boolean dead) {
        deathTicks++;
        if (dead) return; // chÆ°a há»i sinh (Äá»£i AutoRespawn)

        if (!homeSentAfterDeath) {
            if (deathTicks >= deathHomeDelay.get() * 20) {
                sendHomeCommand();
                homeSentAfterDeath = true;
                postHomeTicks = 0;
                info("ÄÃ£ /home sau khi cháº¿t, SellAxe sáº½ báº­t láº¡i sau " + deathRestartDelay.get() + " giÃ¢y.");
            }
            return;
        }

        if (++postHomeTicks >= deathRestartDelay.get() * 20) {
            deathMode = false;
            start(0, false); // ÄÃ£ /home rá»i nÃªn khÃ´ng /home láº§n ná»¯a
        }
    }

    // Freecam cá»§a Meteor lÃ m game ngáº¯m/cháº·t theo camera chá»© khÃ´ng theo nhÃ¢n váº­t.
    // Äáº§u má»i tick: táº¡m ÄÆ°a camera Freecam vá» máº¯t + hÆ°á»ng nhÃ¬n cá»§a nhÃ¢n váº­t Äá» game
    // tÃ­nh tÃ¢m ngáº¯m ÄÃºng; cuá»i tick: tráº£ camera vá» chá» cÅ© cho báº¡n tiáº¿p tá»¥c xem.
    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickSync(TickEvent.Pre event) {
        restoreFreecam();

        if (!workInFreecam.get() || mc.player == null || mc.world == null) return;
        if (stage < 1 || restartMode || deathMode) return;

        Freecam fc = Modules.get().get(Freecam.class);
        if (fc == null || !fc.isActive()) return;

        savedX = fc.pos.x;
        savedY = fc.pos.y;
        savedZ = fc.pos.z;
        savedYaw = fc.yaw;
        savedPitch = fc.pitch;

        Vec3d eye = mc.player.getEyePos();
        fc.pos.set(eye.x, eye.y, eye.z);
        fc.yaw = mc.player.getYaw();
        fc.pitch = mc.player.getPitch();
        syncedFreecam = fc;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickRestore(TickEvent.Post event) {
        restoreFreecam();
    }

    private void restoreFreecam() {
        if (syncedFreecam == null) return;
        syncedFreecam.pos.set(savedX, savedY, savedZ);
        syncedFreecam.yaw = savedYaw;
        syncedFreecam.pitch = savedPitch;
        syncedFreecam = null;
    }

    // Äá»©ng yÃªn quÃ¡ lÃ¢u (bá» káº¹t cuá»i ÄÆ°á»ng nÆ°á»c) -> /home
    private void checkIdle() {
        if (!autoHome.get()) {
            idleAnchor = null;
            idleTicks = 0;
            return;
        }

        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (idleAnchor == null || pos.squaredDistanceTo(idleAnchor) > 1.0) {
            idleAnchor = pos;
            idleTicks = 0;
            return;
        }

        if (++idleTicks >= idleSeconds.get() * 20) {
            sendHome("Äá»©ng yÃªn quÃ¡ lÃ¢u, tá»± /home.");
        }
    }

    // Kiá»m tra cÃ³ Äang cháº·t rÆ°Æ¡ng khÃ´ng (Äang Äáº­p block vÃ  tia ngáº¯m cá»§a nhÃ¢n váº­t trá» vÃ o rÆ°Æ¡ng)
    private boolean isBreakingChest() {
        if (mc.interactionManager == null || mc.world == null) return false;
        if (!mc.interactionManager.isBreakingBlock()) return false;
        HitResult target = mc.player.raycast(4.5, 1.0F, false);
        if (!(target instanceof BlockHitResult hit)) return false;
        if (hit.getType() != HitResult.Type.BLOCK) return false;

        var block = mc.world.getBlockState(hit.getBlockPos()).getBlock();
        return anyBlock.get() || block instanceof ChestBlock;
    }

    // QuÃ¡ lÃ¢u khÃ´ng cháº·t rÆ°Æ¡ng -> /home
    private void checkChest() {
        breakingChest = isBreakingChest();

        if (!noChestHome.get() || breakingChest) {
            noChestTicks = 0;
            return;
        }

        if (++noChestTicks >= noChestSeconds.get() * 20) {
            sendHome("KhÃ´ng cháº·t ÄÆ°á»£c rÆ°Æ¡ng trong " + noChestSeconds.get() + " giÃ¢y, tá»± /home.");
        }
    }

    private void sendHome(String reason) {
        sendHomeCommand();
        info("%s", reason);
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
    }

    // Hiá»n tráº¡ng thÃ¡i ngay trong danh sÃ¡ch module cá»§a Meteor
    @Override
    public String getInfoString() {
        if (deathMode) return "Vá»«a cháº¿t";
        if (restartMode) return "Restart region";
        if (stage < 3) return "Äang khá»i Äá»ng";
        return breakingChest ? "Äang cháº·t rÆ°Æ¡ng" : "KhÃ´ng cháº·t";
    }
}
