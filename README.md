# Possum's Chat Features
## Features
- Chat-only messages with no UI blocking the player view.
- Local welcome message for first-time players.
- Global welcome message for first-time players.
- Local welcome-back message for returning players.
- Scheduled restart countdown announcements.
- Optional sounds for join messages and restart reminders.
- Fake join and leave messages for vanish use.
- In-game color and style changes.
- Operator-only commands.

## Commands

### /chatfeatures enable
Enable the plugin. 

### /chatfeatures disable
Disable the plugin.

### /chatfeatures status
View the current plugin status, theme color, message style, restart countdown status, and restart time.

### /chatfeatures reload
Reload the config file.

### /chatfeatures fakejoin
Display a fake join message for the player running the command.

### /chatfeatures fakeleave
Display a fake leave message for the player running the command.

### /chatfeatures color <color>
Change the main theme color. 

Available colors:
black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white

### /chatfeatures style <style>
Change the message style.

Available styles:
bold, italic, normal

### /chatfeatures debug localWelcomeMessage
Preview the local first-join welcome message.

### /chatfeatures debug localReturnMessage
Preview the local returning player message.

### /chatfeatures debug globalWelcomeMessage
Preview the global first-join message.

### /chatfeatures debug restart
Run a restart countdown demo.

## Configuration

### enabled
Enable or disable the plugin.

### themeColor
Set the main chat color used by `{color}`.

### messages.style
Set the main message style used by `{style}`.

### messages.localWelcomeMessage
Message sent privately to a player when they join for the first time. 

### messages.localReturnMessage
Message sent privately to a player when they return.

### messages.globalWelcomeMessage
Message sent globally whenever a player joins the server for the first time.

### messages.globalWelcomeSubtitle
Subtitle for global welcome messages, if desired.

### join.suppressDefaultJoinMessage
Hide the default join message.

### join.firstJoinSound
Configure the sound played when a player joins for the first time.

### fakeMessages.enabled
Enable or disable the ability to use fake join and leave messages.

### restart.time
The daily restart announcement time.
This does not actually restart the server, only announces it.

### restart.sound
Configure the sound used for restart countdown messages.

### restart.warnings
Configure each restart warning time and message

### restart.goodbye
Configure the final message sent when the restart countdown reaches zero.

## Permissions

### possumschatfeatures.admin
Allows use of `/chatfeatures`.

Default: operator only
