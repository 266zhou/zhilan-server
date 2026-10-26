# RabbitMQ 入门：写给零基础的同学

> **目标**：读完这份文档，你能
> 1. 用自己的话说清 RabbitMQ 是干什么的
> 2. 在管理界面里手动发消息、收消息
> 3. 看懂阶段 3 Part B 的所有代码
>
> **不需要任何 MQ 基础。** 大约 40 分钟读完 + 20 分钟动手。

---

## 目录

1. [先搞懂：消息队列到底是什么](#1-先搞懂消息队列到底是什么)
2. [RabbitMQ 的六个核心概念](#2-rabbitmq-的六个核心概念)
3. [一条消息的完整旅程](#3-一条消息的完整旅程)
4. [四种交换机类型](#4-四种交换机类型)
5. [⭐ 动手：在管理界面玩一玩（不用写代码）](#5--动手在管理界面玩一玩不用写代码)
6. [六个必须搞懂的关键词](#6-六个必须搞懂的关键词)
7. [Spring Boot 里怎么用](#7-spring-boot-里怎么用)
8. [我们项目里怎么用](#8-我们项目里怎么用)
9. [常见坑与排查](#9-常见坑与排查)
10. [命令速查](#10-命令速查)

---

## 1. 先搞懂：消息队列到底是什么

### 1.1 一个生活比喻

**没有消息队列（同步）：**

> 你打电话给餐厅订餐。
> 餐厅说："好的，我马上做，你**在电话里等着**。"
> 你举着电话等了 20 分钟，饭做好了才能挂。

**问题**：你被"占住"了 20 分钟，什么事都干不了。如果同时有 100 个人打电话，餐厅只能接 100 条线，直接瘫痪。

**有消息队列（异步）：**

> 你打电话给餐厅订餐。
> 餐厅说："**记下了，稍后通知你。**"（挂断）
> 你该干嘛干嘛。
> 后厨按自己的节奏，一个一个做。

**好处**：
- 你**不用等**（响应快）
- 餐厅可以**按自己的速度**做（不限并发）
- 100 个人同时打电话也没事（先记下来）

**这就是消息队列的核心价值：把"必须立刻做完"变成"先记下来，慢慢做"。**

### 1.2 为什么秒杀需要它

回到我们的项目：

```
10 万个人同时点"立即抢购"
```

**不用 MQ（同步版，你现在写的）：**

```
每个请求都要：
  Redis 判断（1ms）
  → 扣 MySQL 库存（10ms）
  → 插 MySQL 订单（10ms）
  → 事务提交（5ms）
  = 26ms

并发的 200 个请求会同时抢 MySQL 连接池（只有 10 个连接）
→ 190 个排队等待 → 大量超时 → 用户看到"系统繁忙"
```

**用 MQ（异步版）：**

```
每个请求只需要：
  Redis 判断（1ms）
  → 发一条消息到 MQ（1ms）
  → 立刻返回"排队中"
  = 2ms

真正要扣库存的只有 100 个请求（库存 100 张）
→ 100 条消息进队列
→ 消费者按自己的速度处理，数据库压力平稳
```

### 1.3 消息队列的三个典型用途

| 用途 | 说明 | 我们的项目 |
|---|---|---|
| **异步** | 不需要等结果就能返回 | ✅ 秒杀下单 |
| **削峰** | 把瞬时高峰摊平 | ✅ 10万请求 → 100条消息 |
| **解耦** | 生产者和消费者互不认识 | ✅ 秒杀服务不关心谁建订单 |

### 1.4 RabbitMQ 是什么

**RabbitMQ 是一个"消息队列软件"**，用 Erlang 语言写的，实现了 **AMQP 协议**。

**同类产品对比：**

| 产品 | 语言/生态 | 特点 |
|---|---|---|
| **RabbitMQ** | Erlang | 老牌、稳定、**功能全**、路由灵活、**中小规模首选** |
| Kafka | Scala/Java | **超高性能**、日志流场景、吞吐百万级 |
| RocketMQ | Java | 阿里出品、事务消息强 |
| Redis List | C | 最轻，但功能少 |

> **为什么我们选 RabbitMQ？**
> 你本地已经装了，而且对"秒杀下单"这种每个项目几十万 QPS 以内的场景，**RabbitMQ 完全够用，而且功能最全（确认机制、死信队列、灵活路由）**。
> Kafka 更适合"大数据日志流"，杀鸡用牛刀。

---

## 2. RabbitMQ 的六个核心概念

**这是最关键的一节。搞懂这六个词，RabbitMQ 你就懂 80% 了。**

### 2.0 全景图（先看整体）

```
                        ┌─────────────────────────────────────┐
                        │           RabbitMQ 服务器            │
                        │                                     │
┌──────────┐            │   ┌──────────────┐                  │
│ Producer │──发送消息──▶│   │   Exchange   │  ← 交换机（分拣中心）│
│  生产者  │            │   │  seckill.    │                  │
└──────────┘            │   │  exchange    │                  │
                        │   └──────┬───────┘                  │
                        │          │ Binding（绑定规则）        │
                        │          │ routingKey="seckill.order"│
                        │          ▼                          │
                        │   ┌──────────────┐                  │
                        │   │    Queue     │  ← 队列（真正存消息）│
                        │   │  seckill.    │                  │
                        │   │  order.queue │                  │
                        │   └──────┬───────┘                  │
                        └──────────┼──────────────────────────┘
                                   │ 推送消息
                                   ▼
                            ┌──────────┐
                            │ Consumer │  ← 消费者（取消息干活）
                            │  消费者  │
                            └──────────┘
```

**一句话概括整个流程：**

> **生产者把消息发给「交换机」，交换机按「绑定规则」把消息投进「队列」，消费者从「队列」里取消息处理。**

### 2.1 Producer（生产者）

**发消息的程序。** 在我们的项目里就是**秒杀接口**。

```java
rabbitTemplate.convertAndSend(交换机, routingKey, 消息内容);
```

**生产者只负责"发"，不关心谁收、什么时候收。**

### 2.2 Consumer（消费者）

**收消息的程序。** 在我们的项目里就是**处理订单的类**。

```java
@RabbitListener(queues = "seckill.order.queue")
public void handle(SeckillOrderMessage msg) {
    // 处理消息
}
```

**消费者只是个"监听器"** —— 它不主动去问"有消息吗"，而是**队列有消息了，RabbitMQ 主动推给它**。

> 这叫「**推送模式（Push）**」，对比 Redis 的 List 需要消费者主动 `LPOP`（拉模式）。
> **推模式的好处**：消息一到就能处理，实时性高。

### 2.3 Queue（队列）

**真正存放消息的地方。** 可以理解为**一个先进先出的管道**。

```
消息1 → 消息2 → 消息3 → 消息4
  ↑                          ↑
入队                      出队（消费者取）
```

**关键特性：**

| 特性 | 说明 |
|---|---|
| **消息只会被一个消费者取走** | 多个消费者是"竞争"关系，不是"广播"（广播由交换机实现） |
| **队列是 RabbitMQ 里唯一存消息的地方** | 交换机和绑定都不存消息 |
| 队列有名字 | 如 `seckill.order.queue` |
| 队列可以持久化 | 见 6.1 节 |

### 2.4 Exchange（交换机）

**⭐ 这是最容易搞混的概念。**

**交换机不存消息！它的唯一职责是「分拣」——决定消息该投到哪个队列。**

**类比**：交换机 = **快递分拣中心**

```
你寄一个包裹（消息）到分拣中心（交换机）
分拣中心看面单上的地址（routingKey）
→ 按地址投到对应的货车（队列）
```

**如果交换机找不到匹配的队列，消息会直接丢失！**（这是新手最常见的坑，见 9.1）

### 2.5 Binding（绑定）

**连接交换机和队列的「规则」。**

```
"seckill.exchange"  ──绑定──▶  "seckill.order.queue"
                    routingKey = "seckill.order"
```

**没有绑定 = 交换机和队列没关系 = 消息到不了队列。**

**类比**：绑定 = 分拣中心的**路由表** —— "东南区的包裹走 3 号车"。

### 2.6 Routing Key（路由键）

**一个字符串，用来让交换机判断"该投给谁"。**

```java
rabbitTemplate.convertAndSend(
        "seckill.exchange",      // 交换机
        "seckill.order",         // routingKey ← 就是它
        message);
```

**类比**：routingKey = 包裹上的**邮编/地址分区**。

### 2.7 Virtual Host（虚拟主机，简称 vhost）

**RabbitMQ 里的"命名空间"，可以理解为"数据库"。**

- 一个 RabbitMQ 服务器可以有多个 vhost
- **不同 vhost 之间的队列、交换机完全隔离**（同名也不冲突）
- 用户只能访问被授权的 vhost

**默认 vhost 是 `/`**，我们项目就用默认的。

```yaml
spring:
  rabbitmq:
    virtual-host: /          # 默认就是 /
```

> **为什么需要 vhost？**
> 同一个 RabbitMQ 上跑多个项目，用不同 vhost 隔离，互不干扰。就像 MySQL 里一个实例多个库。

### 2.8 六个概念一张表

| 名词 | 中文 | 作用 | 类比 |
|---|---|---|---|
| **Producer** | 生产者 | 发消息 | 寄件人 |
| **Consumer** | 消费者 | 收消息处理 | 收件人 |
| **Queue** | 队列 | **存消息** | 快递柜 |
| **Exchange** | 交换机 | **分拣消息** | 分拣中心 |
| **Binding** | 绑定 | 交换机和队列的连接规则 | 路由表 |
| **Routing Key** | 路由键 | 分拣依据 | 邮编/地址 |
| **Virtual Host** | 虚拟主机 | 隔离空间 | 数据库 |

---

## 3. 一条消息的完整旅程

**跟着我们的秒杀场景走一遍：**

```
【第 1 步】生产者发消息
────────────────────────────────────────────────
RabbitTemplate.convertAndSend(
    "seckill.exchange",              ← 交换机名
    "seckill.order",                 ← routingKey
    new SeckillOrderMessage(1, 1001) ← 消息体
)

   ↓ 消息转成 JSON：
   {"voucherId":1,"userId":1001}

   ↓ 加上 AMQP 协议头（含 __TypeId__ 等）
   ↓ 通过 TCP 连接（5672 端口）发给 RabbitMQ


【第 2 步】交换机分拣
────────────────────────────────────────────────
RabbitMQ 收到消息，交给 "seckill.exchange"

这个交换机是 Direct 类型 → 精确匹配 routingKey

查它的绑定表：
  binding1: seckill.order.queue  ←  routingKey = "seckill.order"
  
routingKey "seckill.order" 匹配上了！
→ 消息投进 "seckill.order.queue"

⚠️ 如果没有匹配的绑定 → 消息【丢弃】（除非配了备份交换机）


【第 3 步】消息进队列
────────────────────────────────────────────────
消息进 "seckill.order.queue"，排队等待

此时可以在管理界面看到：
  Ready = 1     （1 条待消费）


【第 4 步】RabbitMQ 推送
────────────────────────────────────────────────
RabbitMQ 发现有个消费者在监听这个队列
→ 根据 prefetch 配置（比如 prefetch=1），推 1 条过去

消费者收到 → 消息变成 Unacked（已投递未确认）


【第 5 步】消费者处理
────────────────────────────────────────────────
反序列化 JSON → SeckillOrderMessage
↓
调用 createOrderFromMq()
  → 扣 MySQL 库存
  → 插 MySQL 订单


【第 6 步】手动确认（ack）
────────────────────────────────────────────────
处理成功 → channel.basicAck(deliveryTag, false)
→ 告诉 RabbitMQ "这条我处理完了，可以删了"

RabbitMQ 从队列中删除这条消息

管理界面：Unacked 变 0，Total 变 0


【第 7 步】如果处理失败
────────────────────────────────────────────────
channel.basicNack(deliveryTag, false, false)
→ requeue=false：不重新入队
→ 消息被丢弃（或进死信队列）
```

### 3.1 这里有一个重要的设计决策

**为什么第 4 步是"RabbitMQ 推"，而不是"消费者主动拉"？**

| 模式 | 代表 | 特点 |
|---|---|---|
| **推（Push）** | **RabbitMQ** | 实时性高，但需要 `prefetch` 控制流量 |
| 拉（Pull） | Kafka、Redis List | 消费者自主控制节奏，但有延迟 |

**推模式的风险**：如果 RabbitMQ 一次推 1000 条给消费者，消费者内存会爆。

**所以需要 `prefetch`** → 见 6.3 节。

---

## 4. 四种交换机类型

**交换机按"怎么分拣"分成四种。我们项目用 Direct。**

### 4.1 Direct（直连）⭐ 我们用的

**规则：routingKey 完全相等才投递。**

```
交换机 seckill.exchange（Direct）
  │
  ├── binding: routingKey = "seckill.order"    → 队列 A
  └── binding: routingKey = "cache.delete"     → 队列 B

发消息 routingKey = "seckill.order"  → 只进队列 A ✅
发消息 routingKey = "seckill.ord"    → 谁都匹配不上 → 丢弃 ❌（必须完全一致）
```

**适用**：一对一的精确路由。**最常用、最好理解。**

### 4.2 Fanout（广播）

**规则：忽略 routingKey，投给所有绑定的队列。**

```
交换机 logs.exchange（Fanout）
  │
  ├── binding → 队列 A
  ├── binding → 队列 B
  └── binding → 队列 C

发一条消息 → A、B、C 都收到
```

**适用**：广播通知（如"缓存全量刷新"）。

### 4.3 Topic（主题）

**规则：routingKey 支持通配符匹配。**

```
通配符：
  *  匹配一个单词
  #  匹配零个或多个单词

交换机 topic.exchange
  ├── binding: "order.*"     → 匹配 order.create、order.pay
  └── binding: "*.error"     → 匹配 order.error、pay.error
```

**适用**：复杂的多维度路由。

### 4.4 Headers（头部）

**规则：按消息的 header 属性匹配，不看 routingKey。**

**很少用**，性能比 Direct 差，知道有这个东西即可。

### 4.5 四种类型对比

| 类型 | 路由方式 | 使用频率 | 我们的项目 |
|---|---|---|---|
| **Direct** | routingKey **完全相等** | ⭐⭐⭐⭐⭐ 最常用 | ✅ **用这个** |
| **Fanout** | 广播给所有队列 | ⭐⭐⭐ | — |
| **Topic** | 通配符匹配 | ⭐⭐ | — |
| Headers | 按 header 匹配 | ⭐ 很少用 | — |

### 4.6 还有一个"默认交换机"

RabbitMQ 内置一个**名字为空字符串 `""` 的 Direct 交换机**。

**特殊之处**：每个队列创建时，**自动**绑定到它，routingKey = **队列名**。

```java
// 所以这样写可以直接发到队列（跳过自定义交换机）
rabbitTemplate.convertAndSend("", "seckill.order.queue", message);
                       // ↑ 空字符串 = 默认交换机
                       //              ↑ routingKey = 队列名
```

**新手常看到的写法**，知道原理就行。**我们项目用自定义交换机，更规范。**

---

## 5. ⭐ 动手：在管理界面玩一玩（不用写代码）

**这一节是我最推荐的。动手玩 10 分钟，胜过看一小时理论。**

### 5.1 打开管理界面

浏览器打开：

```
http://localhost:15672
```

**账号密码**：`guest` / `guest`

> ⚠️ **`guest` 用户只能从 localhost 登录**。这是 RabbitMQ 的安全限制。所以你本机能登，远程不行。

### 5.2 认识界面的几个标签页

| 标签 | 作用 |
|---|---|
| **Overview** | 总览：消息速率、连接数、节点状态 |
| **Connections** | 当前 TCP 连接（应用连上来会显示） |
| **Channels** | 连接里的"通道"（一个连接可以开多个通道） |
| **Exchanges** | **交换机列表** |
| **Queues** | **队列列表** ← 最常用 |
| **Admin** | 用户、vhost 管理 |

### 5.3 ⭐ 实验一：创建队列 + 手动发消息

**目标：不写代码，亲眼看到消息进队列、被取出。**

#### 步骤

**① 点 `Queues` 标签 → 点 `Add a new queue`**

填写：

| 字段 | 值 |
|---|---|
| **Type** | `Classic` |
| **Name** | `test.hello` |
| **Durability** | **`Durable`**（持久化） |
| **Auto delete** | `No` |

点 **`Add queue`**。

**② 队列列表里出现了 `test.hello`**

点进去，看到详情页：

```
Overview
  Ready:      0      ← 待消费的消息数
  Unacked:    0      ← 已投递未确认的消息数
  Total:      0      ← 总数
```

**③ 点开 `Publish message` 折叠面板**

填写：

| 字段 | 值 |
|---|---|
| **Payload** | `你好，RabbitMQ` |
| **Delivery mode** | `2 - Persistent` |
| **Headers** | 留空 |

点 **`Publish message`**。

**④ 观察变化**

```
Ready:      1      ← 消息进队列了！
Total:      1
```

**🎉 恭喜，你发出了第一条消息。**

**⑤ 点 `Get messages` 折叠面板**

填写：

| 字段 | 值 |
|---|---|
| **Messages** | `1` |
| **Ack Mode** | **`Nack message requeue true`** ← 先别真消费 |

点 **`Get messages`**。

**看到消息内容了：**

```
你好，RabbitMQ
```

**⚠️ 重要：`Ready` 仍然是 1，这是正确的！**

因为 `Nack message requeue true` 的语义是「**我拿出来看看，看完原样放回去**」：

```
Ready: 1 → (取出) → Ready: 0, Unacked: 1 → (放回) → Ready: 1, Unacked: 0
                                                          ↑
                                                  你看到的是这个终态
```

**消息没有被消费掉，只是"借出来看了一眼"。** 这是最安全的实验方式。

> **`Ack Mode` 三个选项的准确行为**：
>
> | 选项 | 管理界面做了什么 | `Ready` 最终 | `Total` 最终 |
> |---|---|---|---|
> | `Nack message requeue true` | 取出 → 看一眼 → **放回队列** | **仍是 1** | 1 |
> | `Ack message requeue false` | 取出 → 看一眼 → **删除** | **变 0** | **变 0** |
> | `Reject message requeue true` | 取出 → 拒绝 → 放回 | 仍是 1 | 1 |
>
> **想让 `Ready` 变 0 → 用 `Ack message requeue false`。**

#### 💡 这里藏着一个重要概念：为什么消息能"放回去"？

```
消费者拿到消息（basic.get / basic.consume）
    ↓
消息状态：Ready → Unacked（已投递，未确认）
    ↓
三种结局：
    ├─ basicAck                    → 删除消息（真正消费完成）
    ├─ basicNack(requeue = true)   → 退回 Ready（可再次投递）
    └─ 消费者崩溃 / 连接断开        → RabbitMQ 自动把 Unacked 退回 Ready
```

**最后一条就是"消息不丢"的核心机制：**

> **只要消费者没有明确 `ack`，RabbitMQ 就认为"你还没处理完"，消息绝不删除。**
> **消费者进程挂了 → 连接断开 → 消息自动回到队列 → 别的消费者能接手 → 不会丢。**

**这正是我们项目必须用 `acknowledge-mode: manual` 的原因** —— 让代码在「真正处理成功」之后才 ack。
如果用 `none`（自动确认），消息一推出去就被认为处理完了，**处理失败也丢了**。

### 5.4 ⭐ 实验二：交换机 + 绑定

**目标：理解"交换机分拣"这个过程。**

**① 点 `Exchanges` 标签 → `Add a new exchange`**

| 字段 | 值 |
|---|---|
| **Name** | `test.exchange` |
| **Type** | **`direct`** |
| **Durability** | `Durable` |

点 **`Add exchange`**。

**② 点进 `test.exchange`，找到 `Bindings` 区域**

填：

| 字段 | 值 |
|---|---|
| **This exchange** | `test.exchange`（默认） |
| **To queue** | `test.hello` |
| **Routing key** | `test.key` |

点 **`Bind`**。

**现在有了绑定关系：**

```
test.exchange  ──(routingKey=test.key)──▶  test.hello
```

**③ 点进 `test.exchange`，找 `Publish message` 面板**

| 字段 | 值 |
|---|---|
| **Routing key** | **`test.key`** ← 必须和绑定一致 |
| **Payload** | `发给 test.hello 的消息` |

点 **`Publish message`**。

**④ 回到 `Queues` → 点 `test.hello`**

```
Ready: 1     ← 消息通过交换机成功进入队列了！
```

**⑤ ⭐ 关键实验：故意写错 routingKey**

回到 `test.exchange`，再发一条，但 routingKey 填 **`wrong.key`**。

点 Publish。

**结果：`test.hello` 的 Ready 还是 1，没有变成 2。**

**消息去哪了？—— 被丢弃了。**

> **这就是新手最常踩的坑：routingKey 和绑定不匹配 → 消息静默丢失，没有任何报错。**

**这个实验一定要做一遍**，你会对"消息丢失"有切身体会。

### 5.5 ⭐ 实验三：观察消费者（写代码后做）

**这个要等 Part B 代码写完才能做。**

启动应用后：

| 位置 | 你会看到 |
|---|---|
| `Connections` | 多了一条来自你应用的连接 |
| `Channels` | 消费者的通道 |
| `Queues` → `seckill.order.queue` | `Consumers: 1`（有一个消费者在听） |

然后发秒杀请求，观察队列数字涨落：

```
Ready: 100  → 90 → 50 → 10 → 0      （消费者在逐条处理）
```

**这就是"削峰填谷"的视觉呈现。**

### 5.6 实验总结：你验证了 5 件事

- [x] 队列能存消息
- [x] 消息能发布进队列
- [x] 消息能取出查看
- [x] 交换机 + 绑定能把消息投进队列
- [x] **routingKey 不对，消息会静默丢失**

**这 5 件事就是你理解 Part B 代码的全部基础。**

---

## 6. 六个必须搞懂的关键词

**这一节是"面试会问"的内容。生产环境必须考虑这六个问题。**

### 6.1 持久化（Durability / Persistent）

**问题**：RabbitMQ 重启了，消息还在吗？

**答案：取决于两件事，缺一不可。**

| 层级 | 配置 | 作用 |
|---|---|---|
| **队列持久化** | `new Queue(name, true)` | 重启后**队列**还在 |
| **消息持久化** | `MessageDeliveryMode.PERSISTENT` | 重启后**消息**还在 |

**代码：**

```java
// 队列持久化：第二个参数 durable = true
@Bean
public Queue seckillOrderQueue() {
    return QueueBuilder.durable(SECKILL_ORDER_QUEUE).build();
    //                       ↑ 默认就是 durable
}

// 消息持久化：Spring 的 Jackson2JsonMessageConverter
// 默认就是 PERSISTENT，不用特别设置
```

**⚠️ 常见误区**：

> "我配了队列持久化，消息就不会丢了吧？"
> **错。** 队列持久化只保证**队列本身**存在，里面的消息如果没设持久化，重启照样没。

**两个都要。** 我们项目两个都做了（`QueueBuilder.durable()` + Jackson 转换器默认持久化）。

### 6.2 ack（确认机制）

**问题**：消费者拿到消息后，如果**处理到一半崩溃**了，消息会不会丢？

**答案：取决于 ack 模式。**

**三种模式**（Spring 里 `acknowledge-mode`）：

| 模式 | 行为 | 风险 |
|---|---|---|
| **`none`**（自动确认） | 消息一推送就认为处理完了 | ❌ **处理失败消息也丢了** |
| **`auto`**（自动） | 方法正常返回才确认，抛异常不确认 | ⚠️ 有点用但控制不细 |
| **`manual`**（手动）⭐ | **自己在代码里 ack/nack** | ✅ 最可控，**我们用它** |

**手动 ack 的代码：**

```java
try {
    // 处理业务
    voucherOrderService.createOrderFromMq(message);

    // 成功 → 确认
    channel.basicAck(deliveryTag, false);
} catch (Exception e) {
    // 失败 → 拒绝
    channel.basicNack(deliveryTag, false, false);
}
```

**`basicNack` 的第三个参数 `requeue` 是关键决策：**

| requeue | 行为 | 后果 |
|---|---|---|
| `true` | 消息**重新入队** | ⚠️ 如果代码一直失败 → **无限重试死循环**，日志爆炸 |
| `false` | 消息**丢弃**（或进死信） | ✅ 不阻塞队列，但需要补偿机制 |

**我们选 `false`。**

> **面试标准答案**：
> "我们用**手动 ack**，业务处理成功才 `basicAck`。失败时 `basicNack(requeue=false)` 不重回队列（避免死循环），生产环境会配**死信队列**兜底。"

### 6.3 prefetch（预取数量）

**问题**：RabbitMQ 一次推给消费者多少条消息？

**答案：由 `prefetch` 决定。**

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 1        # 一次只推 1 条
```

**对比：**

**`prefetch` 不设（默认 250）：**

```
队列里有 250 条消息
    ↓
RabbitMQ 一次全推给消费者
    ↓
消费者内存里堆了 250 条
    ↓
如果处理慢 → 内存占用高，处理不均匀（有的消费者闲死，有的忙死）
```

**`prefetch: 1`：**

```
队列里有 250 条消息
    ↓
RabbitMQ 只推 1 条
    ↓
消费者处理完 → ack → RabbitMQ 再推下一条
    ↓
内存占用低，负载均衡（谁闲下来给谁）
```

**适用建议：**

| 场景 | prefetch |
|---|---|
| **处理慢（要打数据库）** | **1~10**（我们选 1） |
| 处理快（纯内存计算） | 可以大一些（50~100） |

> **注意**：`prefetch` 是**每个消费者**的数量，不是全局的。

### 6.4 幂等（Idempotency）

**问题**：RabbitMQ 保证"**至少一次**"投递（at-least-once），意味着**可能重复推同一条消息**。如果重复消费，订单会不会建两条？

**这是 MQ 系统里最核心的问题之一。**

**RabbitMQ 不会重复发消息**，但以下情况会导致**消费者收到两次**：

```
场景：消费者处理完了，但 ack 的时候网络断了
    ↓
RabbitMQ 没收到 ack → 认为没处理成功 → 过一会儿【重新推送】
    ↓
消费者又收到同一条消息 → 又处理一遍
```

**解决方案：让处理逻辑"幂等"** —— 执行一次和执行多次，结果一样。

**我们的实现：数据库唯一索引。**

```sql
UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`)
```

```java
try {
    save(order);         // 第二次执行 → 唯一索引冲突 → DuplicateKeyException
} catch (DuplicateKeyException e) {
    // 说明已经处理过了，直接当成功
}
```

**结果：重复消费不会产生第二条订单。**

> **面试标准答案**：
> "RabbitMQ 是 at-least-once 语义，必须消费端幂等。我们用数据库唯一索引 `(user_id, voucher_id)` 兜底，重复插入会抛 `DuplicateKeyException`，捕获后直接 ack，天然幂等。"

### 6.5 死信队列（DLX / Dead Letter Exchange）

**问题**：`basicNack(requeue=false)` 的消息去哪了？

**答案**：默认**直接丢弃**。但可以配置**死信队列**接收。

**什么情况下消息会变成"死信"：**

| 情况 | 说明 |
|---|---|
| 被 reject/nack 且 requeue=false | 我们主动拒绝 |
| **消息 TTL 过期** | 消息在队列里待太久 |
| **队列满了** | 超过队列长度上限 |

**配置死信队列的写法：**

```java
@Bean
public Queue seckillOrderQueue() {
    return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
            .deadLetterExchange("dlx.exchange")       // 死信交换机
            .deadLetterRoutingKey("dlx.routing.key")  // 死信路由键
            .build();
}

// 死信交换机 + 死信队列
@Bean
public DirectExchange dlxExchange() {
    return new DirectExchange("dlx.exchange");
}

@Bean
public Queue dlxQueue() {
    return QueueBuilder.durable("dlx.queue").build();
}

@Bean
public Binding dlxBinding() {
    return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("dlx.routing.key");
}
```

**流程：**

```
seckill.order.queue 处理失败
    ↓ basicNack(requeue=false)
死信交换机 dlx.exchange
    ↓
死信队列 dlx.queue     ← 失败的消息在这里，可以人工排查/重试
```

**我们项目暂时不做**（先跑通主流程），**但你要知道有这个机制**。

### 6.6 消息丢失的四个环节 ⭐

**这是"可靠性"的核心，面试高频。**

```
生产者 ──①──▶ 交换机 ──②──▶ 队列 ──③──▶ 消费者
                                    └──④──▶ (队列/消息本身)
```

| 环节 | 可能的问题 | 解决手段 | 我们做了吗 |
|---|---|---|---|
| **① 生产 → 交换机** | 网络断了，消息没到交换机 | **publisher-confirm** | ✅ |
| **② 交换机 → 队列** | routingKey 不匹配，消息被丢弃 | **publisher-returns** | ✅ |
| **③ 队列 → 消费者** | 消费者处理到一半崩了 | **手动 ack** | ✅ |
| **④ 队列/消息本身** | RabbitMQ 重启，队列/消息没了 | **队列持久化 + 消息持久化** | ✅ |

**四个全做，才算"消息不丢"。**

**代码层面：**

```yaml
# ① ② 的配置
spring:
  rabbitmq:
    publisher-confirm-type: correlated   # ① 到达交换机后回调
    publisher-returns: true              # ② 路由失败后回调
```

```java
@PostConstruct
public void init() {
    // ① 消息到达交换机 → 回调
    rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
        if (!ack) {
            log.error("[MQ] 消息未到达交换机！原因: {}", cause);
            // 生产环境：落库重试
        }
    });

    // ② 消息到了交换机但找不到队列 → 回调
    rabbitTemplate.setReturnsCallback(returned -> {
        log.error("[MQ] 消息路由失败！交换机={}, routingKey={}",
                returned.getExchange(), returned.getRoutingKey());
    });
}
```

**① 和 ② 的区别（容易混）：**

| 回调 | 时机 | 说明 |
|---|---|---|
| **confirm** | 消息**到达交换机**时 | ack=true 表示交换机收到了 |
| **returns** | 消息**从交换机路由不到队列**时 | 只有在 confirm 之后才可能触发 |

**一句话**：
> **confirm 管"到没到交换机"，returns 管"到了交换机但有没有进队列"。**

### 6.7 六个关键词速查表

| 关键词 | 一句话 | 我们的配置 |
|---|---|---|
| **持久化** | 重启后队列和消息还在 | `durable` + `PERSISTENT` |
| **ack** | 消费者确认处理完成 | `manual` 手动 |
| **prefetch** | 一次推几条给消费者 | `1` |
| **幂等** | 重复消费不出错 | 唯一索引 |
| **死信队列** | 失败消息的收容所 | 暂未做 |
| **消息丢失四环节** | 生产/路由/消费/存储 | 四个全做 |

---

## 7. Spring Boot 里怎么用

**这一节是"代码模板"，Part B 会照着写。**

### 7.1 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

> 你 `pom.xml` 里**已经加过了**（阶段 1 就加了）。

### 7.2 配置（application.yaml）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672              # ⚠️ 注意是 5672，不是 15672！
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
```

### 7.3 三个端口别搞混 ⭐

| 端口 | 用途 | 谁来连 |
|---|---|---|
| **5672** | **AMQP 协议** | **你的应用**（代码连这个） |
| **15672** | **管理界面 Web** | **你的浏览器** |
| 25672 | 集群通信 | RabbitMQ 节点之间 |
| 4369 | Erlang 端口映射 | RabbitMQ 内部 |

> **新手最常见的错误**：`application.yaml` 里写了 `port: 15672` → 连接失败。
> **记住：代码连 5672，浏览器看 15672。**

### 7.4 声明交换机/队列/绑定

```java
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "seckill.exchange";
    public static final String QUEUE = "seckill.order.queue";
    public static final String ROUTING_KEY = "seckill.order";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE, true, false);
        //                              名称, durable, autoDelete
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING_KEY);
    }
}
```

**这些 `@Bean` 的作用**：应用启动时，如果 RabbitMQ 里没有这些对象，**Spring 自动创建**。不用手动去管理界面点。

> **小技巧**：把 `EXCHANGE`、`QUEUE`、`ROUTING_KEY` 定义为 `public static final` 常量，生产者和消费者都引用它，**避免手写字符串出错**。

### 7.5 发消息

```java
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(SeckillOrderMessage msg) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, msg);
    }
}
```

**`convertAndSend` 做了什么：**
1. 用 `MessageConverter` 把对象转成 JSON
2. 加上 AMQP 消息头（含 `__TypeId__` 用于反序列化）
3. 通过 TCP 发给交换机

### 7.6 收消息

```java
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    @RabbitListener(queues = QUEUE)
    public void handle(SeckillOrderMessage msg, Channel channel, Message amqpMsg) 
            throws IOException {
        long tag = amqpMsg.getMessageProperties().getDeliveryTag();
        try {
            // 业务处理
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, false);
        }
    }
}
```

**方法参数的三种组合：**

| 参数 | 作用 |
|---|---|
| `SeckillOrderMessage msg` | 消息体（自动反序列化） |
| `Channel channel` | 用于 ack/nack |
| `Message amqpMsg` | 原始消息（取 deliveryTag 等元数据） |

**顺序可以换，但类型必须对。**

### 7.7 消息转换器（重要）

```java
@Bean
public MessageConverter jsonConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
    //                                     ↑ 必须注入 Spring 的 ObjectMapper
}
```

**为什么必须注入 ObjectMapper？**

```java
new Jackson2JsonMessageConverter()             // ❌ 默认 ObjectMapper
new Jackson2JsonMessageConverter(objectMapper) // ✅ Spring 的（带 JavaTimeModule）
```

**默认的不认识 `LocalDateTime`**，消息体里只要有时间字段就报错：

```
Java 8 date/time type LocalDateTime not supported by default
```

### 7.8 完整代码模板

**Project B 里要写的 4 个类：**

| 类 | 职责 | 关键注解 |
|---|---|---|
| `RabbitMQConfig` | 声明交换机/队列/绑定/转换器 | `@Configuration` + `@Bean` |
| `SeckillOrderMessage` | 消息体 DTO | `@Data` |
| `SeckillOrderProducer` | 发消息 | `@Component` |
| `SeckillOrderConsumer` | 收消息 | `@Component` + `@RabbitListener` |

---

## 8. 我们项目里怎么用

**对照你的阶段 3 Part B：**

### 8.1 基础设施

```
交换机：seckill.exchange      类型 Direct，durable
队列：  seckill.order.queue   durable
绑定：  routingKey = "seckill.order"
```

### 8.2 生产流程

```
用户 POST /seckill/1
    ↓
Redis + Lua 判断（库存、一人一单）
    ↓ 成功
发消息 {voucherId:1, userId:1001} 到 seckill.exchange
    ↓
立刻返回"排队中"
```

### 8.3 消费流程

```
@RabbitListener 收到消息
    ↓
扣 MySQL 库存（UPDATE ... WHERE stock > 0）
    ↓
插 MySQL 订单（唯一索引兜底）
    ↓
basicAck
```

### 8.4 关键配置对照

| 配置 | 值 | 为什么 |
|---|---|---|
| `acknowledge-mode` | `manual` | 处理成功才 ack，防丢 |
| `prefetch` | `1` | 每个消费者一次只处理 1 条，减少内存 |
| `publisher-confirm-type` | `correlated` | 知道消息是否到达交换机 |
| `publisher-returns` | `true` | 知道消息是否路由失败 |
| 队列 durable | `true` | 重启后队列还在 |
| 消息持久化 | 默认（Jackson 转换器） | 重启后消息还在 |

### 8.5 一个取舍要知道

**异步化后，"数据库失败回滚 Redis"变难了：**

```
Part A（同步）：DB 失败 → 同一线程立刻回滚 Redis ✅
Part B（异步）：DB 失败发生在消费者线程，它不知道 Redis 预扣该怎么回滚 ⚠️
```

**现状**：DB 失败时消息被 nack 丢弃，**Redis 库存少了一个但没人买到** → 这张券"消失"。

**阶段 4 的定时关单任务会兜底修正**（超时未支付订单回补库存）。

---

## 9. 常见坑与排查

### 9.1 坑一：消息发了但队列里没有 ⭐ 最常见

**排查顺序：**

```
① 交换机和队列【绑定】了吗？
   → 管理界面 Exchanges → 点交换机 → 看 Bindings 区域
   
② routingKey 完全一致吗？
   → 生产者发的 key 必须和绑定的一模一样（大小写、点号都不能差）
   
③ 看 returns 回调有没有报错？
   → 有 "[MQ] 消息路由失败" 日志就说明是路由问题
   
④ 交换机名字写错了吗？
   → 如果交换机不存在，confirm 会返回 ack=false
```

### 9.2 坑二：连接失败 `Connection refused`

**排查：**

```bash
# ① 服务起了吗？
#    Windows：服务管理器找 RabbitMQ
#    或者浏览器能打开 http://localhost:15672

# ② 端口对吗？（最常见）
#    application.yaml 里必须是 5672，不是 15672

# ③ 用户名密码对吗？
#    默认 guest/guest
```

### 9.3 坑三：`LocalDateTime not supported`

**原因**：`MessageConverter` 用了默认的 `ObjectMapper`。

**解决**：

```java
new Jackson2JsonMessageConverter(objectMapper)   // 注入 Spring 的
```

### 9.4 坑四：消息一直重复消费

**原因**：`basicNack(tag, false, true)` 里 `requeue=true`，处理失败后又放回队列，无限循环。

**解决**：
- 改成 `requeue=false`
- 或者检查代码里的异常，修好根因

### 9.5 坑五：消费者收不到消息（但列表里有）

**检查：**

```bash
① 应用启动了吗？
② @RabbitListener(queues = "...") 里的队列名对吗？
③ 管理界面 Queues → 目标队列 → Consumers 显示几？
   如果是 0 → 没有消费者在听
④ 队列是不是在别的 vhost 里？
   → 管理界面右上角可以切换 vhost
```

### 9.6 坑六：`retry` 和 `manual ack` 冲突

**现象**：配置了 `retry.enabled: true` + `acknowledge-mode: manual`，行为难以预测。

**解决**：**二选一**，别混用。

```yaml
listener:
  simple:
    acknowledge-mode: manual
    prefetch: 1
    # retry:
    #   enabled: true        ← 注释掉，我们手动控制
```

### 9.7 排查工具箱

| 工具 | 用途 |
|---|---|
| **管理界面 Queues** | 看 Ready / Unacked / Total，最直观 |
| **管理界面 Get messages** | 偷看消息内容（选 Nack requeue true） |
| **管理界面 Exchanges → Bindings** | 确认绑定关系 |
| `rabbitmqctl list_queues` | 命令行看队列状态 |
| 应用日志 | `[MQ] 已发送`、`[MQ] 收到` |

---

## 10. 命令速查

### 10.1 常用 rabbitmqctl 命令

```bash
# 列出所有队列（含消息数）
rabbitmqctl list_queues name messages messages_ready messages_unacknowledged

# 列出所有交换机
rabbitmqctl list_exchanges

# 列出所有绑定
rabbitmqctl list_bindings

# 列出连接
rabbitmqctl list_connections

# 查看节点状态
rabbitmqctl status

# 清空某个队列（⚠️ 消息全删）
rabbitmqctl purge_queue seckill.order.queue

# 删除队列
rabbitmqctl delete_queue seckill.order.queue
```

> **Windows 上**：这些命令在 `C:\Program Files\RabbitMQ Server\rabbitmq_server-x.x.x\sbin\` 下，
> 需要**管理员权限**的 CMD，或者把该目录加到 PATH。

### 10.2 管理界面最常用的三个操作

| 想干什么 | 怎么操作 |
|---|---|
| **看有多少消息堆积** | `Queues` → 点队列名 → 看 `Ready` |
| **清空测试数据** | `Queues` → 点队列名 → `Purge Messages` |
| **手动发测试消息** | `Queues` → 点队列名 → `Publish message` |

### 10.3 端口速查

| 端口 | 用途 |
|---|---|
| **5672** | 应用连接（AMQP） |
| **15672** | 浏览器管理界面 |
| 25672 | 集群 |
| 4369 | epmd |
| 5671 | AMQP over TLS |

---

## 附录 A：一页纸总结

```
【是什么】
  RabbitMQ 是消息队列。核心作用：异步、削峰、解耦。

【六个概念】
  Producer 生产者 → Exchange 交换机 → (Binding + RoutingKey) → Queue 队列 → Consumer 消费者

【关键认知】
  ① 交换机不存消息，只分拣
  ② 队列才存消息
  ③ routingKey 不匹配 → 消息静默丢弃
  ④ 消息只被一个消费者取走（多个消费者是竞争关系）

【四种交换机】
  Direct（精确匹配，最常用）| Fanout（广播）| Topic（通配符）| Headers（少用）

【可靠性四环节】
  ① 生产→交换机：publisher-confirm
  ② 交换机→队列：publisher-returns
  ③ 队列→消费者：手动 ack
  ④ 队列/消息：持久化 + PERSISTENT

【三个必懂配置】
  acknowledge-mode: manual   处理成功才 ack
  prefetch: 1                一次只推一条
  durable 队列 + 持久化消息   重启不丢

【端口】
  5672 = 应用连（代码里写这个）
  15672 = 浏览器看
```

## 附录 B：学完自测（能答上就 OK）

1. 交换机和队列，谁存消息？
2. 生产者把消息发给谁？交换机还是队列？
3. routingKey 不匹配，消息会怎样？
4. 队列持久化 + 消息不持久化，重启后消息还在吗？
5. `acknowledge-mode: manual` 相比 `none` 好在哪？
6. `prefetch: 1` 解决什么问题？
7. 消息可能在哪些环节丢失？分别怎么防？
8. `basicNack` 的 `requeue=true` 有什么风险？
9. RabbitMQ 为什么需要消费端幂等？
10. `confirm` 回调和 `returns` 回调的区别？

---

*本文档配套阶段 3 Part B 使用。建议先做完第 5 节的手动实验，再开始写代码。*
