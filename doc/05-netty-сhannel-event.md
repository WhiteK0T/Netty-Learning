# Серия Netty: событие, обработчик и конвейер

## Введение
В предыдущем разделе мы объяснили канал в netty, зная, что канал — это мост между обработчиком и сетевым событием.
Эта статья подробно объяснит оставшиеся несколько очень важных частей Netty, Event, Handler и PipeLine.

## ChannelPipeLine

PipeLine — это конвейер, соединяющий канал (Channel) и обработчик, фактически реализация фильтра, который используется для 
управления методом обработки обработчика.

У каждого Channel есть свой конвейер, и он создается автоматически при создании нового Channel.

Сначала взглянем на ChannelPipeline:

```
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>>

```

Как объект Iterable, ChannelPipeline предоставляет ряд методов добавления и удаления, с помощью которых обработчики 
могут быть добавлены или удалены из ChannelPipeline. Поскольку ChannelPipeline — это фильтр, а фильтр должен указывать
порядок соответствующего фильтра, существуют методы addFirst и addLast для добавления разных порядков в ChannelPipeline.

ChannelPipeline наследует два интерфейса ChannelInboundInvoker и ChannelOutboundInvoker.

Сначала взглянем на рабочую блок-схему канала ChannelPipeline:

```
                                             I/O Request
                                             via Channel or
                                             ChannelHandlerContext
                                                    |
+---------------------------------------------------+---------------+
|                           ChannelPipeline         |               |
|                                                  \|/              |
|    +---------------------+            +-----------+----------+    |
|    | Inbound Handler  N  |            | Outbound Handler  1  |    |
|    +----------+----------+            +-----------+----------+    |
|              /|\                                  |               |
|               |                                  \|/              |
|    +----------+----------+            +-----------+----------+    |
|    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
|    +----------+----------+            +-----------+----------+    |
|              /|\                                  .               |
|               .                                   .               |
| ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
|        [ method call]                       [method call]         |
|               .                                   .               |
|               .                                  \|/              |
|    +----------+----------+            +-----------+----------+    |
|    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
|    +----------+----------+            +-----------+----------+    |
|              /|\                                  |               |
|               |                                  \|/              |
|    +----------+----------+            +-----------+----------+    |
|    | Inbound Handler  1  |            | Outbound Handler  M  |    |
|    +----------+----------+            +-----------+----------+    |
|              /|\                                  |               |
+---------------+-----------------------------------+---------------+
|                                  \|/
+---------------+-----------------------------------+---------------+
|               |                                   |               |
|       [ Socket.read() ]                    [ Socket.write() ]     |
|                                                                   |
|  Netty Internal I/O Threads (Transport Implementation)            |
+-------------------------------------------------------------------+
```


Видно, что ChannelPipeline в основном имеет две операции: одна предназначена для чтения входящего трафика, 
а другая — для записи исходящего.

Для операций чтения, таких как Socket.read(), фактически вызывается метод в ChannelInboundInvoker. 
Для внешних запросов записи ввода-вывода вызывается метод в ChannelOutboundInvoker.

Обратите внимание, что порядок обработки входящих и исходящих запросов обратный, как в следующем примере:

```
    ChannelPipeline p = ...;
   p.addLast("1", new InboundHandlerA());
   p.addLast("2", new InboundHandlerB());
   p.addLast("3", new OutboundHandlerA());
   p.addLast("4", new OutboundHandlerB());
   p.addLast("5", new InboundOutboundHandlerX());

```

В приведенном выше коде мы добавили 5 обработчиков в ChannelPipeline, включая 2 InboundHandler, 2 OutboundHandler и 
обработчик, который обрабатывает как вход, так и выход.

Затем, когда канал встречает входящее событие, оно будет обрабатываться в порядке 1, 2, 3, 4 и 5, но только 
InboundHandler может обработать входящее событие, поэтому реальный порядок выполнения — 1, 2 и 5.

Точно так же, когда канал сталкивается с исходящим событием, оно будет выполняться в порядке 5, 4, 3, 2, 1, но 
только outboundHandler может обработать исходящее событие, поэтому реальный порядок выполнения — 5, 4, 3. .

## ChannelHandler

Netty — это платформа, управляемая событиями, и все события обрабатываются Handler. ChannelHandler может обрабатывать 
ввод-вывод, перехватывать ввод-вывод или передавать событие следующему обработчику в ChannelPipeline для обработки.

Структура ChannelHandler очень проста, всего три метода, а именно:

```
void handlerAdded(ChannelHandlerContext ctx) throws Exception;
void handlerRemoved(ChannelHandlerContext ctx) throws Exception;
void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

```

В соответствии с разницей между входящими и исходящими событиями, ChannelHandler можно разделить на две категории,
а именно ChannelInboundHandler и ChannelOutboundHandler.

Поскольку эти два интерфейса являются интерфейсами, реализовать их сложнее, поэтому Netty предоставляет вам три 
реализации по умолчанию: ChannelInboundHandlerAdapter, ChannelOutboundHandlerAdapter и ChannelDuplexHandler. 
Первые два хорошо понятны, они входящие и исходящие, а последний может обрабатывать как входящие, так и исходящие.

ChannelHandler предоставляется с объектом ChannelHandlerContext. Предполагается, что ChannelHandler взаимодействует 
с ChannelPipeline, которому он принадлежит, через объект контекста. Используя объект контекста, ChannelHandler может
передавать события вверх или вниз по течению, динамически изменять конвейер или сохранять информацию 
(с помощью AttributeKey s), которая специфична для обработчика.

## ChannelHandlerContext

Позволяет ChannelHandler взаимодействовать со своим ChannelPipeline и другими обработчиками. Помимо прочего, обработчик 
может уведомлять следующий ChannelHandler в ChannelPipeline, а также динамически изменять ChannelPipeline, к которому 
он принадлежит.

Например, в ChannelHandlerContext вызовите функцию channel(), чтобы получить связанный канал. Связанный Handler можно 
получить, вызвав handler(). События канала запускаются вызовом методов fire\*.

Взглянем на ChannelHandlerContext:

```
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker 

```

Как вы могли заметить на диаграмме, обработчик должен вызывать методы распространения событий в ChannelHandlerContext,
чтобы перенаправить событие следующему обработчику. Эти методы включают в себя:

Методы распространения входящего события:

```
ChannelHandlerContext.fireChannelRegistered()
ChannelHandlerContext.fireChannelActive()
ChannelHandlerContext.fireChannelRead(Object)
ChannelHandlerContext.fireChannelReadComplete()
ChannelHandlerContext.fireExceptionCaught(Throwable)
ChannelHandlerContext.fireUserEventTriggered(Object)
ChannelHandlerContext.fireChannelWritabilityChanged()
ChannelHandlerContext.fireChannelInactive()
ChannelHandlerContext.fireChannelUnregistered()

```

Методы распространения исходящих событий:

```
ChannelHandlerContext.bind(SocketAddress, ChannelPromise)
ChannelHandlerContext.connect(SocketAddress, SocketAddress, ChannelPromise)
ChannelHandlerContext.write(Object, ChannelPromise)
ChannelHandlerContext.flush()
ChannelHandlerContext.read()
ChannelHandlerContext.disconnect(ChannelPromise)
ChannelHandlerContext.close(ChannelPromise)
ChannelHandlerContext.deregister(ChannelPromise)
```

и следующий пример показывает, как обычно, выполняется распространение события:

```
  public class MyInboundHandler extends ChannelInboundHandlerAdapter {
      @Override
      public void channelActive(ChannelHandlerContext ctx) {
          System.out.println("Connected!");
          ctx.fireChannelActive();
      }
  }

  public class MyOutboundHandler extends ChannelOutboundHandlerAdapter {
      @Override
      public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
          System.out.println("Closing ..");
          ctx.close(promise);
      }
  }
```

## Переменные состояния в ChannelHandler

ChannelHandler — это класс Handler. В общем случае экземпляр этого класса может использоваться несколькими каналами при 
условии, что ChannelHandler не имеет общих переменных состояния.

Но иногда нам нужно поддерживать состояние в ChannelHandler, тогда это связано с проблемой переменных состояния в 
ChannelHandler, см. следующий пример:

```
  public interface Message {
       // your methods here
   }

   public class DataServerHandler extends SimpleChannelInboundHandler<Message> {

       private boolean loggedIn;

        @Override
       public void channelRead0(ChannelHandlerContext ctx, Message message) {
           if (message instanceof LoginMessage) {
               authenticate((LoginMessage) message);
               loggedIn = true;
           } else (message instanceof GetDataMessage) {
               if (loggedIn) {
                   ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
               } else {
                   fail();
               }
           }
       }
       ...
   }
```

В этом примере нам нужно аутентифицировать сообщение и сохранить состояние аутентификации после получения LoginMessage,
Поскольку бизнес-логика такая, должна быть переменная состояния.

Поскольку экземпляр обработчика имеет переменную состояния, предназначенную для одного соединения, вам необходимо 
создать новый экземпляр обработчика для каждого нового канала, чтобы избежать состояния гонки, когда клиент, не 
прошедший проверку подлинности, может получить конфиденциальную информацию:

```
  // Create a new handler instance per channel.
  // See ChannelInitializer.initChannel(Channel).
  public class DataServerInitializer extends ChannelInitializer<Channel> {
      @Override
      public void initChannel(Channel channel) {
          channel.pipeline().addLast("handler", new DataServerHandler());
      }
  }

```

Хотя для хранения состояния обработчика рекомендуется использовать переменные-члены, по некоторым причинам вам может 
не понадобиться создавать много экземпляров обработчика. В таком случае вы можете использовать AttributeKey, который 
предоставляется ChannelHandlerContext :

```
  public interface Message {
      // your methods here
  }
 
  @Sharable
  public class DataServerHandler extends SimpleChannelInboundHandler<Message> {
      private final AttributeKey<Boolean> auth =
            AttributeKey.valueOf("auth");
 
      @Override
      public void channelRead(ChannelHandlerContext ctx, Message message) {
          Attribute<Boolean> attr = ctx.attr(auth);
          if (message instanceof LoginMessage) {
              authenticate((LoginMessage) o);
              attr.set(true);
          } else (message instanceof GetDataMessage) {
              if (Boolean.TRUE.equals(attr.get())) {
                  ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
              } else {
                  fail();
              }
          }
      }
      ...
  }

```

Теперь, когда состояние обработчика привязано к ChannelHandlerContext, вы можете добавить один и тот же экземпляр 
обработчика в разные конвейеры:

```
   public class DataServerInitializer extends ChannelInitializer<Channel> {

       private static final DataServerHandler SHARED = new DataServerHandler();

       @Override
       public void initChannel(Channel channel) {
           channel.pipeline().addLast("handler", SHARED);
       }
   }
```

В приведенном выше примере, в котором использовался AttributeKey, вы могли заметить аннотацию @Sharable.
Если ChannelHandler помечен аннотацией @Sharable, это означает, что вы можете создать экземпляр обработчика только 
один раз и добавить его в один или несколько ChannelPipeline несколько раз без состояния гонки.
Если эта аннотация не указана, вам придется создавать новый экземпляр обработчика каждый раз, когда вы добавляете 
его в конвейер, поскольку он имеет неразделяемое состояние, такое как переменные-члены.

> Обратите внимание, что аннотация @Sharable подготовлена для java-документа и не повлияет на фактический эффект выполнения кода.

## Асинхронный обработчик

Как упоминалось ранее, обработчики можно добавить в конвейер, вызвав метод pipe.addLast. Поскольку конвейер представляет
собой структуру фильтра, добавленные обработчики обрабатываются последовательно.

Однако что, если я хочу, чтобы некоторые обработчики выполнялись в новом потоке? Что, если мы хотим, чтобы обработчики,
выполняемые в этих новых потоках, работали не по порядку?

Например, теперь у нас есть 3 обработчика MyHandler1, MyHandler2 и MyHandler3.

Последовательное выполнение записывается так:

```
ChannelPipeline pipeline = ch.pipeline();

   pipeline.addLast("MyHandler1", new MyHandler1());
   pipeline.addLast("MyHandler2", new MyHandler2());
   pipeline.addLast("MyHandler3", new MyHandler3());

```

Если вы хотите, чтобы MyHandler3 выполнялся в новом потоке, вы можете добавить параметр группы, чтобы обработчик 
запускался в новой группе:

```
static final EventExecutorGroup group = new DefaultEventExecutorGroup(16);
ChannelPipeline pipeline = ch.pipeline();

   pipeline.addLast("MyHandler1", new MyHandler1());
   pipeline.addLast("MyHandler2", new MyHandler2());
   pipeline.addLast(group，"MyHandler3", new MyHandler3());

```

Однако обработчики, добавленные DefaultEventExecutorGroup в приведенном выше примере, также будут выполняться 
последовательно. Если вы действительно не хотите выполнять последовательно, вы можете попробовать рассмотреть 
возможность использования UnorderedThreadPoolEventExecutor.
