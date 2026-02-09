# Discord Ready Check Bot

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![JDA](https://img.shields.io/badge/JDA-5.1.2-blue.svg)](https://github.com/DV8FromTheWorld/JDA)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=bugs)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)

![Ready Check Demo](<img width="1145" height="413" alt="Discord_r6Swp72tUH" src="https://github.com/user-attachments/assets/8634e4f3-1388-4197-b3d6-c223086be94a" />)

## Why I Built This

Trying to get a group of friends online at the same time is harder than it should be. Someone would message the server, maybe forget to @ a person, and half the group wouldn't even see it. Plans would fall apart before they started. I wanted a simple way to ping everyone, see who's actually available, and know when people would be on.

So I made a bot that handles all of that. At first it was just slash commands, and I figured that was enough. But once friends started using it, I realized the friction was still too high. People didn't want to type out a full command every time. That's where saved configs and the plain text `r` shortcut came from. You save a ready check once, and from then on you just type `r` in chat. Done.

The other thing that came from real usage was voice channel awareness. Someone would forget to mark themselves ready but they'd already be sitting in a voice channel. So now the bot picks up on that and marks them automatically. Little stuff like that made it go from something that technically worked to something people actually wanted to use. Several people use it daily now across a few servers.

## What It Does

- Ready checks targeting roles, specific users, or both
- Real-time status tracking with buttons (toggle ready, pass, schedule a time)
- Smart time parsing: `r in 15`, `r at 7pm`, `r at 530` all work
- Voice channel awareness, auto-readies users who hop into voice
- Save configurations per server for one-tap reuse with `/r` or just `r`
- Survives restarts, active checks recover from the database automatically
- Filtered mentions so people already in voice don't get pinged unnecessarily

## Commands

```
/ready targets:@GameRole @Alice @Bob    Start a ready check
/r                                      Quick-start from saved configs
/info                                   Bot info
```

**Text shortcuts (no slash needed):**
```
r              Use the most recent saved config, marks you as ready
r in 15        Start a check, ready in 15 minutes
r at 7:30pm    Start a check, ready at a specific time
r at 530       Smart detection figures out 5:30 AM or PM
```

**Buttons on every check:**
- **Toggle Ready** - one click to flip your status
- **Ready At...** - modal popup for scheduling a specific time
- **Pass** - opt out without blocking everyone else
- **Save** - store the config for fast reuse later

## Setup

**You need:**
- Java 21+
- Maven 3.9+
- A Discord bot token
- A Supabase project with `saved_configs` and `ready_checks` tables

**Environment variables:**
```bash
DISCORD_BOT_TOKEN=your_token
SUPABASE_URL=your_supabase_url
SUPABASE_KEY=your_supabase_key
```

**Run with Docker:**
```bash
echo "DISCORD_BOT_TOKEN=your_token" > .env
echo "SUPABASE_URL=your_url" >> .env
echo "SUPABASE_KEY=your_key" >> .env
docker-compose up -d
```

**Or build manually:**
```bash
mvn clean package
java -jar target/ready-check-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Tech Stack

- Java 21, JDA 5.1.2, Maven
- Supabase (Postgres) for persistence via OkHttp + Gson
- ConcurrentHashMap for in-memory state, ScheduledExecutorService for timers
- Docker multi-stage builds for deployment

---

**David Stober** - [LinkedIn](https://www.linkedin.com/in/david-stober-640b08160/) - [GitHub](https://github.com/stoberdl)
