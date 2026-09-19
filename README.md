<p align="center">
    <img src=".github/assets/vanilla-instincts-logo.png" width="160" alt="Vanilla Instincts">
</p>

<h1 align="center">
    <strong>Vanilla Instincts</strong>
</h1>

---

<p align="center">
    🧠 Minecraft mod for smarter, tactical mobs.
</p>

<p align="center">
    <a href="https://github.com/Nullmess/Vanilla-Instincts/stargazers">
        <img src="https://img.shields.io/github/stars/Nullmess/Vanilla-Instincts?style=flat&logo=github" alt="Stars">
    </a>
    <a href="LICENSE">
        <img src="https://img.shields.io/github/license/Nullmess/Vanilla-Instincts?style=flat" alt="License">
    </a>
    <img src="https://hits.sh/github.com/Nullmess/Vanilla-Instincts.svg?label=views" alt="Views">
</p>

<p align="center">
    <img src=".github/assets/vanilla-instincts-cover-725x274.png" alt="Vanilla Instincts preview" width="725">
</p>

---

## ✨ Features

- Smarter perception, memory and decision-making
- Tactical combat, flanking, ambushes and teamwork
- Improved hostile mobs, animals, villagers and villages
- Adaptive progression, rare variants and equipment
- Mob possession with inventory, hunger and dimension travel
- Performance-aware AI designed to remain scalable
- Vanilla-oriented mechanics without replacing Minecraft's core identity

---

## 🧠 Philosophy

Vanilla Instincts makes mobs smarter and more tactical while keeping Minecraft close to vanilla. Inspired by several AI-focused mods, it also brings back a tougher solo experience where mobs punish bad decisions.

---

## 🧩 Compatibility

| Branch | Forge | NeoForge |
| --- | --- | --- |
| 1.6.x | 1.6.1, 1.6.2, 1.6.3, 1.6.4 | — |
| 1.7.x | 1.7, 1.7.1, 1.7.2, 1.7.3, 1.7.4, 1.7.5, 1.7.6, 1.7.7, 1.7.8, 1.7.9, 1.7.10 | — |
| 1.8.x | 1.8, 1.8.1, 1.8.2, 1.8.3, 1.8.4, 1.8.5, 1.8.6, 1.8.7, 1.8.8, 1.8.9 | — |
| 1.9.x | 1.9, 1.9.3, 1.9.4 | — |
| 1.10.x | 1.10, 1.10.2 | — |
| 1.12.x | 1.12, 1.12.1, 1.12.2 | — |
| 1.15.x | 1.15, 1.15.1, 1.15.2 | — |
| 1.16.x | 1.16.1, 1.16.2, 1.16.3, 1.16.4, 1.16.5 | — |
| 1.18.x | 1.18, 1.18.1, 1.18.2 | — |
| 1.19.x | 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4 | — |
| 1.20.x | 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6 | 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6 |
| 1.21.x | 1.21, 1.21.1, 1.21.3, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 | 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 |
| 26.x | 26.1, 26.1.1, 26.1.2, 26.2 | 26.1, 26.1.1, 26.1.2, 26.2 |

---

## 🎮 Usage

Vanilla Instincts supports singleplayer and multiplayer and is intended to be installed on both the client and server.

To control a mob, switch to **Spectator mode**, spectate the target mob and run:

```text
/vanillainstincts control
```

Run the command again to release control.

---

## 🛠️ Build

Build a specific Forge target:

```sh
./gradlew build -Ptarget=forge-1.21.11
```

Build a specific NeoForge target:

```sh
./gradlew build -Ptarget=neoforge-26.3
```

Build all supported targets:

```sh
./gradlew buildAll
```

Build all Forge targets:

```sh
./gradlew buildAllForge
```

Build all NeoForge targets:

```sh
./gradlew buildAllNeoForge
```

Run Minecraft integration tests for a specific target:

```sh
./gradlew gameTest -Ptarget=neoforge-1.21.10
```

Clean generated build files:

```sh
./gradlew cleanAll
```

Running `./gradlew build` without `-Ptarget` prints a valid target usage example.

---

## 👤 Author

Give a ⭐️ if Vanilla Instincts made your Minecraft worlds more dangerous!

---

## 📄 License

Copyright © 2026 [Nullmess](https://github.com/Nullmess).<br />
This project is licensed under the [MIT License](LICENSE).
