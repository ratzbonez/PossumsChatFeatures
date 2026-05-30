package main.java.possumschatfeatures;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PossumsChatFeatures extends JavaPlugin implements Listener, TabExecutor {

    private boolean enabled;
    private static final String PREFIX = "&a🐀 &2&lChat features &8»";

    // Restart reminder sound settings
    private boolean restartSoundEnabled;
    private Sound restartSound;
    private float restartSoundVolume;
    private float restartSoundPitch;
    private float restartSoundPitchStep;

    // Restart schedule settings
    private LocalTime restartTime;
    private ZoneId restartZone;

    // Manage color and style
    private String themeColorName;
    private String themeColorCode;
    private String messageStyleName;
    private String messageStyleCode;

    // Accepted message styles
    private static final List<String> AVAILABLE_STYLES = Arrays.asList(
            "bold",
            "italic",
            "normal");

    // Accepted colors
    // https://www.digminecraft.com/lists/color_list_pc.php
    private static final List<String> AVAILABLE_COLORS = Arrays.asList(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white");

    // Welcome and return messages.
    private String localWelcomeMessage;

    private String localReturnMessage;

    private String globalWelcomeMessage;
    private String globalWelcomeSubtitle;

    // Toggles for defult join and fake messages.
    private boolean suppressDefaultJoinMessage;
    private boolean fakeMessagesEnabled;

    // First-join sound settings.
    private boolean firstJoinSoundEnabled;
    private Sound firstJoinSound;
    private float firstJoinSoundVolume;
    private float firstJoinSoundPitch;

    // Enable scheduled restart messages all together.
    private boolean restartEnabled;

    // Restart announcements (from config)
    private final Map<Integer, RestartAnnouncement> restartWarnings = new HashMap<>();
    private RestartAnnouncement goodbyeAnnouncement;

    // Tracks the daily restart countdown.
    private ZonedDateTime currentRestartTarget;
    private final Set<Integer> sentWarnings = new HashSet<>();
    private long lastSecondsUntilRestart = -1;

    // Track scheduled debug demo tasks. (Safe cancel)
    private final List<Integer> sampleRestartTaskIds = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("chatfeatures") != null) {
            getCommand("chatfeatures").setExecutor(this);
            getCommand("chatfeatures").setTabCompleter(this);
        }

        startRestartCountdownTask();

        getLogger().info("PossumsChatFeatures enabled. Features: " + statusText(enabled));
    }

    @Override
    public void onDisable() {
        cancelSampleRestartDebug();
    }

    // For loading the config file.
    private void loadSettings() {
        enabled = getConfig().getBoolean("enabled", true);

        themeColorName = normalizeColorName(getConfig().getString("themeColor", "aqua"));
        themeColorCode = colorCodeFor(themeColorName);

        messageStyleName = normalizeMessageStyle(getConfig().getString("messages.style", "bold"));
        messageStyleCode = styleCodeFor(messageStyleName);

        restartSoundEnabled = getConfig().getBoolean("restart.sound.enabled", true);
        restartSound = readSound("restart.sound.sound", Sound.BLOCK_NOTE_BLOCK_BELL);
        restartSoundVolume = (float) getConfig().getDouble("restart.sound.volume", 0.8);
        restartSoundPitch = (float) getConfig().getDouble("restart.sound.pitch", 1.2);
        restartSoundPitchStep = (float) getConfig().getDouble(
                "restart.sound.pitchStep",
                getConfig().getDouble("restart.sound.pitchstep", 0.15));

        localWelcomeMessage = getConfig().getString(
                "messages.localWelcomeMessage",
                "{color}{style}Welcome to Moonshire, &f{style}{username}{color}{style}!");

        localReturnMessage = getConfig().getString(
                "messages.localReturnMessage",
                "{color}{style}Welcome back to Moonshire, &f{style}{username}{color}{style}!");

        globalWelcomeMessage = getConfig().getString(
                "messages.globalWelcomeMessage",
                "{color}{style}{username} &f{style}has joined Moonshire for the first time.");

        globalWelcomeSubtitle = getConfig().getString(
                "messages.globalWelcomeSubtitle",
                "&f{style}Welcome them!");

        suppressDefaultJoinMessage = getConfig().getBoolean("join.suppressDefaultJoinMessage", false);
        fakeMessagesEnabled = getConfig().getBoolean("fakeMessages.enabled", true);

        firstJoinSoundEnabled = getConfig().getBoolean("join.firstJoinSound.enabled", true);
        firstJoinSound = readSound("join.firstJoinSound.sound", Sound.BLOCK_AMETHYST_BLOCK_CHIME);
        firstJoinSoundVolume = (float) getConfig().getDouble("join.firstJoinSound.volume", 1.0);
        firstJoinSoundPitch = (float) getConfig().getDouble("join.firstJoinSound.pitch", 1.3);

        restartEnabled = getConfig().getBoolean("restart.enabled", true);
        restartTime = parseRestartTime(getConfig().getString("restart.time", "00:00"));
        restartZone = parseZone(getConfig().getString("restart.timezone", null));
        currentRestartTarget = findNextRestartTarget(ZonedDateTime.now(restartZone));
        sentWarnings.clear();
        lastSecondsUntilRestart = -1;

        loadRestartWarnings();

        goodbyeAnnouncement = readRestartAnnouncement(
                "restart.goodbye",
                "{color}{style}Server restarting now. &f{style}See you soon!");
    }

    private void saveEnabledSetting() {
        getConfig().set("enabled", enabled);
        saveConfig();
    }

    // On player join event handling
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled)
            return;

        Player player = event.getPlayer();

        if (suppressDefaultJoinMessage) {
            event.setJoinMessage(null);
        }

        if (player.hasPlayedBefore()) {
            sendLocalReturn(player);
            return;
        }

        sendLocalWelcome(player);
        sendGlobalFirstJoinChat(player);
        playFirstJoinSound();
    }

    private void sendLocalWelcome(Player player) {
        player.sendMessage(format(localWelcomeMessage, player));
        playRestartChime(player);
    }

    private void sendLocalReturn(Player player) {
        player.sendMessage(format(localReturnMessage, player));
        playRestartChime(player);
    }

    private void sendGlobalFirstJoinChat(Player newPlayer) {
        if (!isBlank(globalWelcomeMessage)) {
            Bukkit.broadcastMessage(format(globalWelcomeMessage, newPlayer));
        }

        if (!isBlank(globalWelcomeSubtitle)) {
            Bukkit.broadcastMessage(format(globalWelcomeSubtitle, newPlayer));
        }
    }

    private void playFirstJoinSound() {
        if (!firstJoinSoundEnabled || firstJoinSound == null)
            return;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(
                    onlinePlayer.getLocation(),
                    firstJoinSound,
                    firstJoinSoundVolume,
                    firstJoinSoundPitch);
        }
    }

    // Restart warnings configuration.
    private void loadRestartWarnings() {
        restartWarnings.clear();

        ConfigurationSection warningsSection = getConfig().getConfigurationSection("restart.warnings");

        if (warningsSection == null)
            return;

        for (String key : warningsSection.getKeys(false)) {
            try {
                int seconds = Integer.parseInt(key);

                if (seconds <= 0) {
                    getLogger().warning("Ignoring restart warning '" + key + "' because it must be greater than 0.");
                    continue;
                }

                restartWarnings.put(seconds, readRestartAnnouncement(
                        "restart.warnings." + key,
                        ""));
            } catch (NumberFormatException e) {
                getLogger().warning("Ignoring restart warning '" + key + "' because it is not a number.");
            }
        }
    }

    private RestartAnnouncement readRestartAnnouncement(String path, String defaultChat) {
        String chat = getConfig().getString(path + ".chat", defaultChat);

        return new RestartAnnouncement(chat);
    }

    private void startRestartCountdownTask() {
        Bukkit.getScheduler().runTaskTimer(this, this::checkRestartCountdown, 20L, 20L);
    }

    private void checkRestartCountdown() {
        if (!enabled)
            return;
        if (!restartEnabled)
            return;

        ZonedDateTime now = ZonedDateTime.now(restartZone);

        if (currentRestartTarget == null) {
            currentRestartTarget = findNextRestartTarget(now);
            sentWarnings.clear();
            lastSecondsUntilRestart = -1;
        }

        if (!now.isBefore(currentRestartTarget)) {
            sendRestartAnnouncement(goodbyeAnnouncement, -1);

            currentRestartTarget = findNextRestartTarget(now.plusSeconds(1));
            sentWarnings.clear();
            lastSecondsUntilRestart = -1;
            return;
        }

        long secondsUntilRestart = secondsUntil(now, currentRestartTarget);

        if (lastSecondsUntilRestart == -1) {
            lastSecondsUntilRestart = secondsUntilRestart + 1;
        }

        for (Map.Entry<Integer, RestartAnnouncement> entry : restartWarnings.entrySet()) {
            int warningSecond = entry.getKey();

            boolean crossedWarningTime = warningSecond <= lastSecondsUntilRestart
                    && warningSecond >= secondsUntilRestart;

            if (crossedWarningTime && !sentWarnings.contains(warningSecond)) {
                sendRestartAnnouncement(entry.getValue(), warningSecond);
                sentWarnings.add(warningSecond);
            }
        }

        lastSecondsUntilRestart = secondsUntilRestart;
    }

    private long secondsUntil(ZonedDateTime now, ZonedDateTime target) {
        long millisUntil = Duration.between(now, target).toMillis();

        if (millisUntil <= 0) {
            return 0;
        }

        return (long) Math.ceil(millisUntil / 1000.0);
    }

    private void sendRestartAnnouncement(RestartAnnouncement announcement, int warningSecond) {
        if (announcement == null)
            return;

        if (!isBlank(announcement.chat())) {
            broadcastRestartChat(announcement.chat());
        }

        if (warningSecond >= 1 && warningSecond <= 5) {
            playRestartCountdownSound(warningSecond);
            return;
        }

        playRestartChime();
    }

    private void broadcastRestartChat(String message) {
        if (isBlank(message))
            return;

        Bukkit.broadcastMessage(colorize(message));
    }

    private void playRestartChime() {
        playRestartChime(restartSoundPitch);
    }

    private void playRestartChime(float pitch) {
        if (!restartSoundEnabled || restartSound == null)
            return;

        float safePitch = clampPitch(pitch);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(
                    onlinePlayer.getLocation(),
                    restartSound,
                    restartSoundVolume,
                    safePitch);
        }
    }

    // Restart sounds.
    private void playRestartChime(Player player) {
        if (!restartSoundEnabled || restartSound == null || player == null)
            return;

        player.playSound(
                player.getLocation(),
                restartSound,
                restartSoundVolume,
                clampPitch(restartSoundPitch));
    }

    private void playRestartCountdownSound(int countdownSecond) {
        playRestartChime(pitchForRestartSecond(countdownSecond));
    }

    private float pitchForRestartSecond(int countdownSecond) {
        return restartSoundPitch - ((6 - countdownSecond) * restartSoundPitchStep);
    }

    private float clampPitch(float pitch) {
        if (pitch < 0.1f) {
            return 0.1f;
        }

        if (pitch > 2.0f) {
            return 2.0f;
        }

        return pitch;
    }

    // Restart demo
    private void runSampleRestartDebug(CommandSender sender) {
        cancelSampleRestartDebug();

        sender.sendMessage(colorize(PREFIX + " &aStarting restart demo (30 seconds)"));

        scheduleSampleRestartMessage(0L, 30);
        scheduleSampleRestartMessage(20L * 20L, 10);
        scheduleSampleRestartMessage(25L * 20L, 5);
        scheduleSampleRestartMessage(26L * 20L, 4);
        scheduleSampleRestartMessage(27L * 20L, 3);
        scheduleSampleRestartMessage(28L * 20L, 2);
        scheduleSampleRestartMessage(29L * 20L, 1);
        scheduleSampleGoodbye(30L * 20L);
    }

    private void scheduleSampleRestartMessage(long delayTicks, int seconds) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            RestartAnnouncement announcement = restartWarnings.get(seconds);

            if (announcement != null && !isBlank(announcement.chat())) {
                sendRestartAnnouncement(announcement, seconds);
                return;
            }

            broadcastRestartChat(defaultRestartChat(seconds));

            if (seconds >= 1 && seconds <= 5) {
                playRestartCountdownSound(seconds);
            }
        }, delayTicks);

        sampleRestartTaskIds.add(task.getTaskId());
    }

    private void scheduleSampleGoodbye(long delayTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (goodbyeAnnouncement != null && !isBlank(goodbyeAnnouncement.chat())) {
                sendRestartAnnouncement(goodbyeAnnouncement, -1);
                return;
            }

            broadcastRestartChat("{color}{style}Server restarting now. &f{style}See you soon!");
            playRestartChime();
        }, delayTicks);

        sampleRestartTaskIds.add(task.getTaskId());
    }

    // Fake join/leave messages

    private void sendFakeJoin(CommandSender sender) {
        if (!fakeMessagesEnabled) {
            sender.sendMessage(colorize(PREFIX + " &cFake join/leave messages are disabled in config."));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use this command.");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " joined the game");
    }

    private void sendFakeLeave(CommandSender sender) {
        if (!fakeMessagesEnabled) {
            sender.sendMessage(colorize(PREFIX + " &cFake join/leave messages are disabled in config."));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use this command.");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " left the game");
    }

    private void cancelSampleRestartDebug() {
        for (Integer taskId : sampleRestartTaskIds) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        sampleRestartTaskIds.clear();
    }

    private String defaultRestartChat(int seconds) {
        if (seconds == 1) {
            return "{color}{style}Restarting in &f{style}1{color}{style}...";
        }

        if (seconds <= 5) {
            return "{color}{style}Restarting in &f{style}" + seconds + "{color}{style}...";
        }

        return "{color}{style}Automated server restart in &f{style}" + seconds + " seconds{color}{style}.";
    }

    // Time/Sound parsing
    private ZonedDateTime findNextRestartTarget(ZonedDateTime now) {
        ZonedDateTime target = now
                .withHour(restartTime.getHour())
                .withMinute(restartTime.getMinute())
                .withSecond(0)
                .withNano(0);

        if (!target.isAfter(now)) {
            target = target.plusDays(1);
        }

        return target;
    }

    private LocalTime parseRestartTime(String rawTime) {
        if (rawTime == null)
            return LocalTime.MIDNIGHT;

        String cleaned = rawTime.trim().toLowerCase(Locale.ROOT).replace(" ", "");

        if (cleaned.equals("12am") || cleaned.equals("midnight")) {
            return LocalTime.MIDNIGHT;
        }

        if (cleaned.equals("12pm") || cleaned.equals("noon")) {
            return LocalTime.NOON;
        }

        try {
            return LocalTime.parse(cleaned, DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception ignored) {
        }

        try {
            return LocalTime.parse(cleaned.toUpperCase(Locale.ROOT), DateTimeFormatter.ofPattern("h:mma"));
        } catch (Exception ignored) {
            getLogger().warning("Invalid restart.time '" + rawTime + "'. Falling back to 00:00.");
            return LocalTime.MIDNIGHT;
        }
    }

    private ZoneId parseZone(String rawZone) {
        if (rawZone == null || rawZone.trim().isEmpty()) {
            return ZoneId.systemDefault();
        }

        try {
            return ZoneId.of(rawZone.trim());
        } catch (Exception e) {
            getLogger().warning("Invalid restart.timezone '" + rawZone + "'. Falling back to server timezone.");
            return ZoneId.systemDefault();
        }
    }

    private Sound readSound(String path, Sound fallback) {
        String soundName = getConfig().getString(path, fallback.name());

        if (soundName == null || soundName.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound '" + soundName + "'. Falling back to " + fallback.name() + ".");
            return fallback;
        }
    }

    // Message formatting.
    private String format(String message, Player player) {
        if (message == null)
            return "";

        String formatted = message;

        if (player != null) {
            formatted = formatted.replace("{username}", player.getName());
        }

        return colorize(formatted);
    }

    private String colorize(String message) {
        if (message == null)
            return "";

        return ChatColor.translateAlternateColorCodes('&', applyThemeColor(message));
    }

    private String applyThemeColor(String message) {
        if (message == null)
            return "";

        return message
                .replace("{themeColor}", themeColorCode)
                .replace("{color}", themeColorCode)
                .replace("{style}", messageStyleCode)
                .replace("{stlye}", messageStyleCode);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String statusText(boolean value) {
        return value ? "&aENABLED" : "&cDISABLED";
    }

    private String neutralText(String value) {
        return "&e" + value;
    }

    // Helpers for color and style.
    private String normalizeColorName(String rawColorName) {
        if (rawColorName == null || rawColorName.trim().isEmpty()) {
            return themeColorName;
        }

        String normalized = rawColorName.trim().toLowerCase(Locale.ROOT).replace("-", "_");

        if (!AVAILABLE_COLORS.contains(normalized)) {
            getLogger().warning("Invalid themeColor '" + rawColorName + "'. Falling back to light_purple.");
            return themeColorName;
        }

        return normalized;
    }

    private String colorCodeFor(String colorName) {
        return switch (colorName) {
            case "black" -> ChatColor.BLACK.toString();
            case "dark_blue" -> ChatColor.DARK_BLUE.toString();
            case "dark_green" -> ChatColor.DARK_GREEN.toString();
            case "dark_aqua" -> ChatColor.DARK_AQUA.toString();
            case "dark_red" -> ChatColor.DARK_RED.toString();
            case "dark_purple" -> ChatColor.DARK_PURPLE.toString();
            case "gold" -> ChatColor.GOLD.toString();
            case "gray" -> ChatColor.GRAY.toString();
            case "dark_gray" -> ChatColor.DARK_GRAY.toString();
            case "blue" -> ChatColor.BLUE.toString();
            case "green" -> ChatColor.GREEN.toString();
            case "aqua" -> ChatColor.AQUA.toString();
            case "red" -> ChatColor.RED.toString();
            case "yellow" -> ChatColor.YELLOW.toString();
            case "white" -> ChatColor.WHITE.toString();
            case "light_purple" -> ChatColor.LIGHT_PURPLE.toString();
            default -> themeColorName.toString();
        };
    }

    private String normalizeMessageStyle(String rawStyleName) {
        if (rawStyleName == null || rawStyleName.trim().isEmpty()) {
            return "bold";
        }

        String normalized = rawStyleName.trim().toLowerCase(Locale.ROOT).replace("-", "_");

        if (!AVAILABLE_STYLES.contains(normalized)) {
            getLogger().warning("Invalid messages.style '" + rawStyleName + "'. Falling back to bold.");
            return "bold";
        }

        return normalized;
    }

    private String styleCodeFor(String styleName) {
        return switch (styleName) {
            case "bold" -> "&l";
            case "italic" -> "&o";
            case "normal" -> "";
            default -> "&l";
        };
    }

    // Config changing commands
    private void setMessageStyle(CommandSender sender, String rawStyleName) {
        String normalized = normalizeMessageStyle(rawStyleName);

        getConfig().set("messages.style", normalized);
        saveConfig();

        reloadConfig();
        loadSettings();

        sender.sendMessage(colorize(PREFIX + " &aMessage style set to &e" + normalized + "&a."));
    }

    private void setThemeColor(CommandSender sender, String rawColorName) {
        String normalized = normalizeColorName(rawColorName);

        getConfig().set("themeColor", normalized);
        saveConfig();

        reloadConfig();
        loadSettings();

        sender.sendMessage(colorize(PREFIX + " &aTheme color set to &e" + normalized + "&a."));
    }

    // Main command handler
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("possumschatfeatures.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "enable" -> {
                enabled = true;
                saveEnabledSetting();
                sender.sendMessage(colorize(PREFIX + " &aChat features enabled."));
                return true;
            }

            case "disable" -> {
                enabled = false;
                saveEnabledSetting();
                cancelSampleRestartDebug();
                sender.sendMessage(colorize(PREFIX + " &cChat features disabled."));
                return true;
            }

            case "status" -> {
                sender.sendMessage(colorize(PREFIX + " &fChat features: " + statusText(enabled)));
                sender.sendMessage(colorize(
                        PREFIX + " &fTheme color: " + themeColorCode + themeColorName));
                sender.sendMessage(
                        colorize(PREFIX + " &fMessage style: " + neutralText(messageStyleCode + messageStyleName)));
                sender.sendMessage(colorize(PREFIX + " &fRestart countdown: " + statusText(restartEnabled)));
                sender.sendMessage(
                        colorize(PREFIX + " &fRestart time: " + neutralText(restartTime.toString())));
                return true;
            }

            case "reload" -> {
                reloadConfig();
                loadSettings();
                sentWarnings.clear();
                lastSecondsUntilRestart = -1;
                cancelSampleRestartDebug();

                sender.sendMessage(colorize(PREFIX + " &aConfig reloaded."));
                return true;
            }

            case "debug" -> {
                handleDebugCommand(sender, label, args);
                return true;
            }

            case "fakejoin" -> {
                sendFakeJoin(sender);
                return true;
            }

            case "fakeleave" -> {
                sendFakeLeave(sender);
                return true;
            }

            case "color" -> {
                if (args.length != 2) {
                    sender.sendMessage(colorize(PREFIX + " &cUsage: /" + label + " color <color>"));
                    sender.sendMessage(
                            colorize(PREFIX + " &fAvailable colors: &e" + String.join(", ", AVAILABLE_COLORS)));
                    return true;
                }

                setThemeColor(sender, args[1]);
                return true;
            }

            case "style" -> {
                if (args.length != 2) {
                    sender.sendMessage(colorize(PREFIX + " &cUsage: /" + label + " style <bold|italic|normal>"));
                    sender.sendMessage(
                            colorize(PREFIX + " &fAvailable styles: &e" + String.join(", ", AVAILABLE_STYLES)));
                    return true;
                }

                setMessageStyle(sender, args[1]);
                return true;
            }

            default -> {
                sendUsage(sender, label);
                return true;
            }
        }
    }

    // Debug command handling
    private void handleDebugCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(colorize(PREFIX + " &cUsage: /" + label
                    + " debug <localWelcomeMessage|localReturnMessage|globalWelcomeMessage|restart>"));
            return;
        }

        String debugTarget = args[1].toLowerCase(Locale.ROOT);

        if (debugTarget.equals("restart")) {
            if (args.length == 2) {
                runSampleRestartDebug(sender);
                return;
            }

            sender.sendMessage(colorize(neutralText("/" + label + " debug restart")));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can preview welcome messages.");
            return;
        }

        switch (debugTarget) {
            case "localwelcomemessage" -> {
                sendLocalWelcome(player);
            }

            case "localreturnmessage" -> {
                sendLocalReturn(player);
            }

            case "globalwelcomemessage" -> {
                sendGlobalFirstJoinChat(player);
                playFirstJoinSound();
            }

            case "simulatewelcome" -> {
                sendLocalWelcome(player);
                sendGlobalFirstJoinChat(player);
                playFirstJoinSound();
            }

            default -> sender.sendMessage(colorize(PREFIX + " &cUnknown debug message: &f" + args[1]));
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(colorize(PREFIX + " &cUsage:"));
        sender.sendMessage(colorize("/" + label + " enable"));
        sender.sendMessage(colorize("/" + label + " disable"));
        sender.sendMessage(colorize("/" + label + " status"));
        sender.sendMessage(colorize("/" + label + " reload"));
        sender.sendMessage(colorize("/" + label + " fakejoin"));
        sender.sendMessage(colorize("/" + label + " fakeleave"));
        sender.sendMessage(colorize("/" + label + " color <color>"));
        sender.sendMessage(colorize("/" + label + " style <style>"));
        sender.sendMessage(colorize("/" + label + " debug localWelcomeMessage"));
        sender.sendMessage(colorize("/" + label + " debug localReturnMessage"));
        sender.sendMessage(colorize("/" + label + " debug globalWelcomeMessage"));
        sender.sendMessage(colorize("/" + label + " debug restart"));
    }

    // Tab completion
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("possumschatfeatures.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return filterStartingWith(args[0],
                    Arrays.asList("enable", "disable", "status", "reload", "fakejoin", "fakeleave", "color", "style",
                            "debug"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            return filterStartingWith(args[1], AVAILABLE_COLORS);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("style")) {
            return filterStartingWith(args[1], AVAILABLE_STYLES);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return filterStartingWith(args[1], Arrays.asList(
                    "localWelcomeMessage",
                    "localReturnMessage",
                    "globalWelcomeMessage",
                    "restart"));
        }

        return new ArrayList<>();
    }

    private List<String> filterStartingWith(String input, List<String> options) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                matches.add(option);
            }
        }

        return matches;
    }

    // Restart announcement chat text.
    private record RestartAnnouncement(String chat) {
    }
}