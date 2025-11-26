# SkittleEssentials
**Version:** 1.5.0  
**Author:** Twotoz  
A powerful all-in-one essentials plugin designed for the SkittleMC survival minecraft server.  
Includes staff tools, jail systems, baltop rewards, chat systems, new player filter, build mode safety tools, and more.

---

## 📦 Dependencies
This plugin requires:

- ProtocolLib  
- Vault  
- LuckPerms  
- Essentials  

Make sure these are installed before running the plugin.

---

# ⚙️ Features Overview

## 👥 Fake Player Spoofer
Make your server appear more populated in the server list.

- Customizable fake player count  
- Fake player hover-names  
- Toggleable in config  

---

## 🧑‍🎓 New Player Filter System
Restrict commands for players with low playtime.

- Blocks specific commands or command arguments  
- Custom playtime threshold  
- Hidden restrictions (“No permission.”)  
- Helps protect economy and prevent abuse  

---

## 🛠 Build Mode System
A safe building environment for staff:

- Blocks dangerous or valuable blocks (netherite, spawners, beacons, etc.)
- Prevents chest, vault, kits, or auction commands  
- Prevents item duplication or cheating  
- Fully customizable  

---

## 🚓 JailBan System
A full prison system with bail mechanics:

- Defined jail region with teleport  
- Custom jail chat  
- Players earn $1 bail per mob kill  
- Blocked interactions (chests/furnaces/etc)  
- Allowed commands whitelist  
- Boundary escape messages with cooldown  

---

## 🗳 Jail Vote System
Democratic community voting to jail problematic players.

- Cost to start a vote  
- Custom vote duration  
- Bail amount after successful vote  

---

## 💰 Baltop Rewards System
Automatically rewards the richest players with LuckPerms groups.

- Updates every X minutes  
- Custom groups for #1, #2, and #3  
- Works with Essentials economy  

---

## 🗣 StaffChat System
Private staff-only communication.

- Use `!` before a message  
- Fully customizable prefix & format  

---

## 🗨 LocalChat System
Chat only with players in a radius.

- Toggle with `/localchat` or use `?message`  
- Customizable radius  
- LocalChatSpy mode for staff  

---

## 🔍 Player Sizer System
Scale players up or down.

- Default scale range for normal players  
- Extended scale range for OPs (0.01 → 25.0)  
- Automatic attribute scaling  
- Supports permanent size mode  

---

# 🧭 Commands

### `/sizer`
Change your own or another player's size.

/sizer <scale> /sizer <player> <scale>

Permissions:  
- `skittle.sizer.use`  
- `skittle.sizer.other`

---

### `/buildmode`
Toggle build mode.

/buildmode <on|off>

Permission: `skittle.buildmode`

---

### `/jailban`
Send a player to jail.

/jailban <player> <bail> [reason]

Permission: `skittle.jailban`

---

### `/unjailban`
Release a jailed player.

/unjailban <player>

Permission: `skittle.jailban`

---

### `/jaillist`
Show jailed players and their bailout progress.

/jaillist

---

### `/jailbal`
Check your jail balance.

/jailbal

---

### `/bail`
Bail yourself out.

/bail [confirm]

---

### `/startjailvote`
Start a jail vote.

/startjailvote [confirm]

Permission: `skittle.jailvote.start`

---

### `/jailvote`
Vote for who should be jailed.

/jailvote <player>

---

### `/staffchat`
Toggle staff chat.

/staffchat Aliases: /sct, /sc

Permission: `skittle.staffchat`

---

### `/localchat`
Toggle local chat.

/localchat Aliases: /lc, /lchat

Permission: `skittle.localchat.use`

---

### `/localchatspy`
Toggle local chat spy mode.

/localchatspy Aliases: /lcspy, /lspy

Permission: `skittle.localchat.spy`

---

### `/jailchatspy`
View jail chat messages.

/jailchatspy Aliases: /jcs, /jspy

Permission: `skittle.jailban.spy`

---

### `/skittle`
Reload the plugin.

/skittle reload

Permission: `skittle.admin`

---

# 🔒 Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `skittle.sizer.use` | Change your own size | true |
| `skittle.sizer.other` | Change other players' size | op |
| `skittle.sizer.admin` | Use full scale range | op |
| `skittle.sizer.permanent` | Keep size permanently | false |
| `skittle.buildmode` | Use build mode | op |
| `skittle.jailban` | JailBan players | op |
| `skittle.jailban.bypass` | Bypass jail restrictions | op |
| `skittle.jailban.notify` | Receive jail notifications | op |
| `skittle.jailban.spy` | Spy on jail chat | op |
| `skittle.jailvote.start` | Start jail votes | true |
| `skittle.staffchat` | Use staff chat | op |
| `skittle.localchat.use` | Use local chat | false |
| `skittle.localchat.spy` | Spy on local chat | op |
| `skittle.newplayerfilter.bypass` | Bypass new player filter | op |
| `skittle.admin` | Reload plugin | op |

### Wildcard

skittle.*

Grants access to **all plugin features**.

---

# 📥 Installation
1. Download the latest release.  
2. Install all dependencies.  
3. Place the plugin `.jar` in `/plugins`.  
4. Restart your server.  
