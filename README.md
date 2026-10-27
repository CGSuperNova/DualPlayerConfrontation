# DualPlayerConfrontation（双军对峙）

基于 **Paper 1.20.4**（Java 17）的双人对战小游戏插件：
一场游戏共两人，分别出生在岩浆海之上的两座沙塔顶端。玩家不能直接攻击对手，
只能使用系统发放的五种玩法道具摧毁对方塔下的立足方块，使对手坠入岩浆。
一局对战多轮（每回合更换玩法，先死亡方输一回合），**先获得 7 分者胜出**。

```
/duel join  → 匹配 → 竞技场世界克隆加载 → 每回合：准备→开战→(死亡判定)→结算
先到 7 分 → 胜出 → 统计/经验/星级/任务/奖励命令
```

---

## 一、快速开始

1. 把 `DualPlayerConfrontation-1.0.0.jar` 放入 `plugins/`，重启服务器。
2. 首次启动会生成 `plugins/DualPlayerConfrontation/`：
   - `config.yml`   全局配置（规则、五种玩法参数、经验/星级、任务、奖励命令等）
   - `messages.yml` 全部消息文本（支持 `&` 颜色与占位符）
   - `maps.yml`     管理员配置的地图注册表
3. 用 **Multiverse-Core** 导入/创建若干竞技场地图世界（塔+岩浆，双塔中心对称更佳），
   然后用管理命令配置（见下）。
4. 玩家使用 `/duel join` 排队匹配；管理员可用 `/dueladmin force <p1> <p2>` 直接开局。

> 软依赖：`PlaceholderAPI`（提供 `%duel_...%` 占位符）、`Multiverse-Core`（管理地图模板世界）。
> 两者缺失时插件照常工作（地图模板也可以是普通世界）。

### 与 DeluxeHub（大厅插件）的兼容

竞技场使用**固定的槽位世界名**：`duel-1`、`duel-2` … `duel-<slots>`（可在 `config.yml` 的
`arena.name-prefix` / `arena.slots` 中修改）。请在 DeluxeHub 配置中把这些世界加入其
“不管理/黑名单”的世界列表（例如 `duel-1` … `duel-8`），并对 DeluxeHub 执行 `/dh reload`
（或重启）。

即使某个保护类插件拦截了岩浆/坠落/虚空等“回合致死伤害”，本插件也内置兜底：
`rules.force-death-on-cancelled-lethal-damage`（默认开）会在这些伤害被第三方取消时
强制该玩家死亡，保证回合不会被外部插件卡死。

---

## 二、玩法设计回顾

### 规则（全程生效）
1. 玩家**不能徒手破坏/放置方块**（建造被禁止，回合方块由插件回滚）。
2. 玩家只受**岩浆（火焰）、坠落、虚空**伤害，其余伤害一律免疫；
   准备/结算阶段完全免疫；对局中受创不会掉血回血异常。
3. 对**非玩家实体**（如矿车）的攻击/击打效果保留原版。
4. 一回合 **2 分钟**未分胜负 → 地图上空每 5 秒随机生成一颗坠落 TNT；
   **3 分钟**未分胜负 → **岩浆开始从塔底逐格上升**（默认每 3~4 秒上升一格），
   淹没塔身与立足点，逼出胜负。
5. 玩家不能丢弃物品；对局中掉落物快速清除且不能拾取。
6. 所有爆炸物**只破坏方块**，无伤害、无击退、不点燃烧毁（火焰蔓延已禁用）。

### 五种玩法（回合制抽取，轮回制：全部玩过一遍前不重复）
| 玩法 | 说明 |
| --- | --- |
| 火焰弹乒乓球 | 每 4~8 秒获得火焰弹；右键发射（初速度可调，默认较慢以便击打）。火球**击中任何物体（方块/实体）→ 原地生成顺爆TNT**（按 `fireball.radius` 只破坏方块）；徒手击打火球会使其**朝玩家瞄准方向**飞去并可再次击打。 |
| 天降好运 | 天空生成悬浮TNT；玩家获得火矢+无限弓与一支箭，火焰箭射中 TNT 使其坠落轰炸；**每隔 `regen-interval-seconds` 秒自动补满被射掉的悬浮TNT**；箭命中后会立即被删除，不再悬空。 |
| 投掷苦力怕 | 每 4~8 秒获得苦力怕（10% 闪电苦力怕）；右键抛物线投掷；另有 1/2/3 档力度调节道具。**苦力怕所有行为与原版一致**：可被攻击击退、靠近玩家才自爆，唯一区别是爆炸不伤害玩家、无冲击（仍破坏方块）。 |
| 超级潜影贝导弹 | 手持发射器 0.2 秒/发连发导弹（飞行速度已调慢）；命中方块会**摧毁约一格半径的区域**（约7~19个方块，`shulker.destroy-radius` 可调）；左键可打掉来袭导弹；双方导弹靠近会互相吸引并抵消；导弹可穿过玩家。 |
| TNT羽毛球 | 系统在双方塔顶**轮流召唤一颗TNT**（约5秒后自爆、爆炸半径约5格）；玩家持有**小/大击退棒**，攻击TNT可把它以抛物线抽向对面（小棒近、大棒远）；**当前TNT爆炸后才会在另一座塔顶刷出下一颗**。 |

### 回合结束 → 旁观
单个回合结算期间（约 3 秒），双方会切换为**旁观模式**，可自由飞行观战；
下一回合准备开始前自动恢复生存模式并回到塔顶。

---

## 三、地图配置（管理）

```text
/duelmap create <id> [world]                以当前/指定世界为模板创建地图
/duelmap center <id> [x y z]                站在地图中心设置中心点
/duelmap tower <id> <a|b>                   两次执行记录塔区域两个对角点
/duelmap tower <id> <a|b> x1 y1 z1 x2 y2 z2 直接输入塔区域（左下角/右上角）
/duelmap spawn <id> <a|b> [x y z]           站在塔顶设置玩家出生点（不设置则自动取塔顶）
/duelmap list / info <id> / tp <id>
/duelmap remove <id> / enable <id> / disable <id>
```

对局开始前，插件会**复制模板世界文件夹 → 以固定槽位世界名独立加载副本**
（默认 `duel-1` … `duel-8`，可多局并行、随时重置），结束后卸载并删除副本。
因此地图模板请保持“原始未破坏”状态（请勿把模板世界用作对局现场）。
`duel-*` 前缀为副本专用，请勿把其他世界命名为该前缀；固定槽位名也便于
DeluxeHub 等插件按世界名加入黑名单。

推荐的地图制作用例：
1. MV 创建 `duelmap_1`（平地/superflat）并搭好两座沙塔与岩浆；
2. `/duelmap create mymap duelmap_1`；
3. 飞到塔顶：`/duelmap center mymap` 设中心；分别站到两塔角落执行两遍 `/duelmap tower mymap a|b`；
4. `/duelmap enable mymap`，即可开局。

### 音效
已在关键位置加入音效：准备倒计时提示音、回合开始/模式播报、回合胜负音、
整局胜利烟花声与失败音、经验获得与升星音、玩法道具音效等。

---

## 四、命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/duel join` | 进入匹配队列（星级相近优先） | duel.play |
| `/duel leave` | 离开队列 | duel.play |
| `/duel stats [玩家]` | 查看战绩/星级/经验 | duel.play |
| `/duel quests` | 查看每日/每周任务进度 | duel.play |
| `/duel help` | 帮助 | - |
| `/duelmap ...` | 地图配置（见上） | duel.admin |
| `/dueladmin force <p1> <p2> [map]` | 强制开局 | duel.admin |
| `/dueladmin roundwin\|roundlose <玩家>` | 强制某人赢/输本回合 | duel.admin |
| `/dueladmin gamewin\|gamelose <玩家>` | 强制某人赢/输整局 | duel.admin |
| `/dueladmin end [玩家]` | 强制结束对局（不结算） | duel.admin |
| `/dueladmin games / stats <玩家> / reload / debug <玩家>` | 管理/调试 | duel.admin |

---

## 五、统计与星级

- 统计项（YAML 存储于 `data/<uuid>.yml`）：游玩/胜/负、胜率、
  破坏方块数、平均回合耗时（胜/负/综合）、火球发射/打回数、激活TNT数、
  投掷苦力怕数（含闪电）、发射/打掉导弹数。
- 经验结算（`win-score` 默认 7，公式自动使用该值）：
  - 胜利：`win-base * (60 / 本局平均回合秒) * (win-score - 对方赢的回合) / win-score + win-bonus`（默认 50/25）
  - 失败：`lose-per-round * 自己赢下的回合`（默认每回合 5）
- 星级（累计经验不清零，从 1 星开始）：
  星数 <100：每 50 经验升一星；<500：每 100；<1000：每 200；1000+：每 500。
- 奖励：`rewards.win / rewards.lose` 中配置控制台/玩家执行的命令，支持占位符
  `{player} {winner} {loser} {score} {rounds}`。

---

## 六、任务系统

- 内置**每日任务**与**每周任务**（每自然日/每周一重置），支持类型：
  `WIN_GAMES / PLAY_GAMES / BLOCKS_DESTROYED / FIREBALLS_SHOT / FIREBALLS_HIT /
  TNTS_ACTIVATED / CREEPERS_THROWN / CHARGED_CREEPERS_THROWN / BULLETS_SHOT / BULLETS_HIT`。
- 完成任务奖励**经验**并可执行**奖励命令**（`quests.run-as` 控制台/玩家；
  占位符 `{player} {quest} {xp}`）。
- 配置于 `config.yml` 的 `quests.daily / quests.weekly`，用 `/dueladmin reload` 重载。
- 查看进度：`/duel quests`。进度在每局结束后自动结算。

---

## 七、PlaceholderAPI 占位符

前缀 `%duel_`：

```text
duel_stars / duel_star        当前星级
duel_xp / duel_xp_to_next / duel_xp_into_star / duel_xp_progress
duel_games / duel_wins / duel_losses / duel_winrate
duel_blocks_destroyed / duel_fireballs_shot / duel_fireballs_hit
duel_tnt_activated / duel_creepers_thrown / duel_charged_creepers_thrown
duel_bullets_shot / duel_bullets_hit
duel_avg_round_won / duel_avg_round_lost / duel_avg_round_all
duel_in_queue / duel_in_game（yes/no）
duel_game_round / duel_game_mode / duel_game_score /
duel_game_opponent_score / duel_game_opponent
duel_prefix                    形如 [1⭐]
```

---

## 八、配置自定义

`config.yml` 中几乎所有数值与开关都可改：
- 规则（可受伤来源、掉落物清理间隔、是否冻结准备阶段、爆炸无伤害/无击退、
  是否启用“致死伤害被第三方取消时强制死亡”兜底…）
- **竞技场槽位**（`arena.name-prefix` / `arena.slots`，同时决定最大并发对局数）
- 五种玩法的道具/间隔/槽位/爆炸半径/速度/力度/概率、TNT补货/羽毛球参数…
- 超时与岩浆（2 分钟TNT阶段、3 分钟岩浆上升：起始时间、上升间隔、填充范围…）
- 匹配（目标分 win-score（默认7）、并发对局数、按星级匹配…）
- 任务系统（`quests.daily/weekly` 的定义、目标、经验与命令奖励）
- 大厅返回点、经验公式、星级经验档、奖励命令
- 记分板开关与文本

> 修改后用 `/dueladmin reload` 重载（地图配置在 `maps.yml`，用 `/duelmap` 命令编辑）。

---

## 九、构建

```bash
mvn clean package
# 产出 target/DualPlayerConfrontation-1.0.0.jar
```

依赖：`io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT`（provided）、
`me.clip:placeholderapi:2.11.6`（provided，运行时可选）。无运行时第三方库需要打包（不 shade）。

### 向上兼容注意（未来升级版本）
- 目标语言 Java 17，`api-version: '1.20'`。
- 附魔、粒子等易变枚举通过 `NamespacedKey`/`valueOf` 运行时探测取得。
- 未直接依赖 Multiverse API（其世界仅作为模板被读取复制），减少版本绑定；
  如需深度集成可基于 `ArenaWorldManager` 扩展。
- 实体/物品均使用 PersistentDataContainer 打标，不依赖 NMS。
- 破坏/回滚使用区块级 Bukkit API（`BlockData`/`setType`），版本稳定面大。

---

## 十、代码结构

```
com.buzizi.dualplayerconfrontation
├── DualPlayerConfrontation        主类
├── config/                        config.yml / messages.yml 封装
├── data/MapDefinition             地图定义（中心/双塔/出生点）
├── game/                          对局状态机、玩法池、方块回滚、竞技场
│   ├── DuelGame                   回合/胜负/超时/岩浆上升/结算
│   ├── ArenaInstance              副本世界句柄
│   └── BlockChangeLog             方块改动日志与回滚
├── manager/                       地图注册表 / 竞技场世界 / 对局 / 匹配队列 / 任务系统
├── modes/                         五种玩法实现（火焰弹/天降好运/苦力怕/导弹/TNT羽毛球）
├── listeners/                     全局规则 + 玩家生命周期
├── cmd/                           /duel /duelmap /dueladmin
├── papi/DuelExpansion             PlaceholderAPI
├── stats/                         玩家档案 / 星级计算
└── util/                          文本、区域、PDC标签、跨版本工具
```
