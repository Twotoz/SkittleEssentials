# SkittleEssentials
**Version:** 1.5.0  
**Author:** Twotoz  
**Description:** A combination of essential server features for modern Minecraft servers.  
**Minecraft API Version:** 1.20  
**Main Class:** `twotoz.skittleEssentials.SkittleEssentials`

SkittleEssentials provides a collection of powerful tools for server management, moderation, chat control, and gameplay enhancements — all bundled into a single plugin.

---

## 📦 Dependencies
This plugin requires the following dependencies:

- **ProtocolLib**
- **Vault**
- **LuckPerms**
- **Essentials**

Make sure these are installed before using the plugin.

---

## 🚀 Features
- Player size scaling with permissions and admin override.
- Fully featured jail system (bail, chat, vote, monitoring).
- StaffChat and LocalChat with toggle modes and spy tools.
- Build Mode for staff, builders, or admins.
- Live configuration reload via `/skittle reload`.

---

# 🧭 Commands

### `/sizer`
Change your own size or another player's size.
```

/sizer <scale>
/sizer <player> <scale>

```
**Permission:** `skittle.sizer.use`  
**Other players:** `skittle.sizer.other`

---

### `/buildmode`
Toggle Build Mode.
```

/buildmode <on|off>

```
**Permission:** `skittle.buildmode`

---

### `/jailban`
Send a player to jail with a bail amount.
```

/jailban <player> <bail_amount> [reason]

```
**Permission:** `skittle.jailban`

---

### `/unjailban`
Release a jailed player.
```

/unjailban <player>

```
**Permission:** `skittle.jailban`

---

### `/jaillist`
Show all jailed players and their bail progress.
```

/jaillist

```
**Permission:** `skittle.jailban`

---

### `/jailbal`
Check your jail balance and bail amount.
```

/jailbal

```

---

### `/bail`
Bail yourself out or check bail progress.
```

/bail [confirm]

```

---

### `/startjailvote`
Start a community jail vote.
```

/startjailvote [confirm]

```
**Permission:** `skittle.jailvote.start`

---

### `/jailvote`
Vote for who should be jailed.
```

/jailvote <player>

```

---

### `/staffchat`
Toggle staff chat mode.
```

/staffchat
Aliases: /sct, /sc

```
**Permission:** `skittle.staffchat`

---

### `/localchat`
Toggle local chat mode.
```

/localchat
Aliases: /lc, /lchat

```
**Permission:** `skittle.localchat.use`

---

### `/localchatspy`
Toggle spying on all local messages.
```

/localchatspy
Aliases: /lcspy, /lspy

```
**Permission:** `skittle.localchat.spy`

---

### `/jailchatspy`
Toggle jail chat spy mode for staff.
```

/jailchatspy
Aliases: /jcs, /jspy

```
**Permission:** `skittle.jailban.spy`

---

### `/skittle`
Reload the plugin configuration.
```

/skittle reload

```
**Permission:** `skittle.admin`

---

# 🔒 Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `skittle.sizer.use` | Change your own size | true |
| `skittle.sizer.other` | Change size of other players | op |
| `skittle.sizer.admin` | Use full scale range (0.01–25.0) | op |
| `skittle.sizer.permanent` | Keep size permanently | false |
| `skittle.buildmode` | Use Build Mode | op |
| `skittle.jailban` | Jailban players | op |
| `skittle.jailban.bypass` | Bypass jail restrictions | op |
| `skittle.jailban.notify` | Receive jail notifications | op |
| `skittle.jailban.spy` | Spy on jail chat | op |
| `skittle.jailvote.start` | Start jail votes | true |
| `skittle.staffchat` | Use staff chat & see staff messages | op |
| `skittle.localchat.use` | Use local chat | false |
| `skittle.localchat.spy` | Spy on all local chat | op |
| `skittle.newplayerfilter.bypass` | Bypass new player filter restrictions | op |
| `skittle.admin` | Reload the plugin | op |

### Wildcard: `skittle.*`
Grants access to **all** SkittleEssentials permissions.

---

# 📥 Installation
1. Download the latest SkittleEssentials release.  
2. Install the required dependencies (ProtocolLib, Vault, LuckPerms, Essentials).  
3. Place the `.jar` file in your server's `/plugins` folder.  
4. Restart your server.

