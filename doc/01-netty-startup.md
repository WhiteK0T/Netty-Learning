# Серия Netty: Netty startup  

## Введение в Netty

Netty — отличный фреймворк NIO. Первое представление о NIO у всех должно быть более сложным, особенно при работе с 
различными протоколами HTTP, TCP и UDP, его очень сложно использовать. Но netty обеспечивает удобную инкапсуляцию 
этих протоколов, и программирование ввода-вывода может быть выполнено быстро и лаконично с помощью netty. 
С Netty легко разработать, она хорошо работает и сочетает в себе стабильность и гибкость. 
Если вы хотите разрабатывать высокопроизводительные сервисы, всегда правильно использовать netty.

На 02.10.2022 последняя версия netty 4.1.82.Final. Фактически, это самая стабильная официально рекомендуемая версия.
У Netty также есть версия 5.x, но официально ее не рекомендуют использовать.

Если мы хотим использовать Netty в своем проекте, то нужно добавить зависимость:

```
        <!-- https://mvnrepository.com/artifact/io.netty/netty-all -->
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
            <version>4.1.82.Final</version>
        </dependency>

```

Ниже мы испытаем прелесть netty на самом простом примере.

## Первый сервер Netty

Что такое сервер? Программу, которая может предоставлять услуги внешнему миру, можно назвать сервером. 
Создание сервера — это первый шаг во всех внешних службах. Как использовать netty для создания сервера? 
Сервер в основном отвечает за обработку запросов от различных программ. Netty предоставляет класс 
ChannelInboundHandlerAdapter для обработки таких запросов. Нам нужно только наследовать этот класс.

В NIO каждый канал является каналом связи между клиентом и сервером. ChannelInboundHandlerAdapter определяет 
некоторые события и ситуации, которые могут возникнуть в этом канале, как показано на следующем рисунке:

![](https://img-blog.csdnimg.cn/8ed5c09fd5d4443487994e57ad5a8bb8.png)

Как показано на рисунке выше, в канале может произойти множество событий, таких как установление соединения, 
закрытие соединения, чтение данных, завершение чтения, регистрация, отмена регистрации и т. д. 
Эти методы можно переопределить, нужно только создать новый класс, наследующий ChannelInboundHandlerAdapter.

Создадим новый класс FirstServerHandler и переопределяем два метода channelRead и exceptionCaught, 
Метод channelRead предназначен для чтения сообщений из канала, а exceptionCaught для обработки исключений.

```
public class FirstServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // обработка сообщения
        ByteBuf in = (ByteBuf) msg;
        try {
            log.info("Получил сообщение :{}", in.toString(CharsetUtil.UTF_8));
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Обработчик ошибок
        log.error("Ошибка", cause);
        ctx.close();
    }
}

```

В приведенном выше примере после получения сообщения мы вызываем метод release(), чтобы освободить его, не обрабатывая его.
Вызов метода очистки является обычной практикой после завершения использования сообщения. Приведенный выше код преобразует
msg в ByteBuf. Если вы не хотите конвертировать, вы можете использовать его напрямую следующим образом:

```
        try {
            // обработка сообщения
        } finally {
            ReferenceCountUtil.release(msg);
        }
```

В методе обработки исключений мы печатаем информацию об исключении и закрываем контекст.

Нам нужно создать новый класс Server и использовать FirstServerHandler для создания каналов и получения сообщений. 
Далее, давайте взглянем на поток обработки сообщений Netty.

В Netty обработка ввода-вывода реализована с использованием многопоточного цикла обработки событий. EventLoopGroup 
в Netty — это абстрактный класс этих циклов событий.

Давайте взглянем на структуру классов EventLoopGroup.

![](https://img-blog.csdnimg.cn/fe7d545f9a3647fc8383e57c8e3391d8.png)

Видно, что EventLoopGroup наследуется от EventExecutorGroup, а EventExecutorGroup наследуется от ScheduledExecutorService,
поставляемого с JDK.

Таким образом, EventLoopGroup по сути является службой пула потоков. Он называется Group, потому что содержит множество 
циклов EventLoop, а цикл обработки событий можно пройти, вызвав метод next.

EventLoop используется для обработки информации ввода-вывода, зарегистрированной в канале EventLoop. EventLoop — это 
Executor, который выполняется путем непрерывной отправки задач. Конечно, EventLoop может регистрировать несколько каналов,
но обычно это не используется.

EventLoopGroup объединяет несколько циклов событий в группу, и с помощью next метода можно пройти циклы событий 
в группе. Кроме того, EventLoopGroup предоставляет несколько методов для регистрации канала в текущем EventLoop.

Как видно из рисунка выше, возвращаемым результатом метода register является ChannelFuture. Всем известно, что Future 
можно использовать для получения результата выполнения асинхронных задач. Тот же ChannelFuture также является носителем
асинхронного результата, который можно заблокировать вызовом метод синхронизации, пока не будет получен результат 
выполнения.

Как видите, метод register также может передаваться в объект ChannelPromise. ChannelPromise является одновременно 
подклассом ChannelFuture и Promise, а Promise является особым подклассом Future, который может управлять состоянием Future.

EventLoopGroup имеет реализации многих подклассов (например: NioEventLoopGroup, OioEventLoopGroup, EpollEventLoopGroup, 
KQueueEventLoopGroup, LocalEventLoopGroup, SingleThreadEventLoop), мы будем использовать NioEventLoopGroup этот класс 
реализован на Selector-ах для выбора каналов. Еще одна особенность заключается в том, что NioEventLoopGroup может 
добавлять дочерние группы EventLoopGroups.

Для NIO сервера нам нужны две группы Threads, одна группа называется bossgroup, которая в основном используется
для мониторинга соединений, а другая группа называется workerGroup, используется для обработки рабочей нагрузки каналов,
принятых боссом. Эти соединения необходимо зарегистрировать в группе сервера (ServerBootstrap), чтобы продолжить.

Передав эти две группы в ServerBootstrap, можно запустить службу из ServerBootstrap. Соответствующий код выглядит 
следующим образом:

```
        //Создаем две группы Eventloop для обработки соединений и сообщений.
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new FirstServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Привязываем порт и начинаем получать соединения
            ChannelFuture f = b.bind(port).sync();
```

Созданный нами в начале FirstServerHandler был добавлен в pipeline childHandler при инициализации канала.

Таким образом, при наличии вновь установленного канала FirstServerHandler будет использоваться для обработки данных канала.

В приведенном выше примере мы также указали несколько параметров ChannelOptions для установки некоторых свойств канала.

Наконец, мы привязываем соответствующий порт и запускаем сервер.

Выше мы написали сервер и запустили его, а теперь нам нужен клиент для взаимодействия с ним.

Если вы не хотите писать код, вы можете напрямую использовать telnet localhost 8888 для взаимодействия с сервером, 
но здесь мы хотим использовать API-интерфейс netty для создания клиента для взаимодействия с сервером.

Процесс создания сетевого клиента в основном такой же, как и процесс создания сетевого сервера. Во-первых, вам нужно 
создать обработчик для обработки определенных сообщений Аналогично, здесь мы также наследуем ChannelInboundHandlerAdapter.

В предыдущем разделе упоминалось, что в ChannelInboundHandlerAdapter есть много методов, которые можно переписать 
в соответствии с потребностями вашего собственного бизнеса. Здесь мы хотим отправить сообщение на сервер, когда канал 
активен. Затем вам нужно переписать метод channelActive, а также выполнить некоторую обработку исключения, 
поэтому вам также необходимо переписать метод exceptionCaught. Если вы хотите обрабатывать, когда канал читает сообщение,
вы можете переопределить метод channelRead.

Созданный код FirstClientHandler выглядит следующим образом:

```
@Slf4j
public class FirstClientHandler extends ChannelInboundHandlerAdapter {

    private ByteBuf content;
    private ChannelHandlerContext ctx;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        content = ctx.alloc().directBuffer(256).writeBytes("Hello World".getBytes(StandardCharsets.UTF_8));
        // Отправляем сообщение
        sayHello();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Обработчик ошибок
        log.error("Abnormal", cause);
        ctx.close();
    }

    private void sayHello() {
        // output message to server
        // Пишем сообщения на сервер
        ctx.writeAndFlush(content.retain());
    }
}

```

В приведенном выше коде мы сначала запрашиваем ByteBuff из ChannelHandlerContext, а затем вызываем его метод writeBytes
для записи данных, которые должны быть переданы. Наконец, вызываем метод writeAndFlush ctx для отправки сообщения на сервер.

Следующим шагом будет запуск клиентского класса, на стороне сервера мы построили две NioEventLoopGroups, которые обрабатывают
выбор канала и чтение сообщений в канале. Для клиента этой проблемы не существует, здесь нужна только одна NioEventLoopGroup.

Серверная сторона использует ServerBootstrap для запуска службы, а клиентская сторона использует Bootstrap, 
бизнес-логика его запуска в основном такая же, как и у запуска сервера:

```
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new FirstClientHandler());
                        }
                    });

            // connect to the server
            ChannelFuture f = b.connect(HOST, PORT).sync();
```

## Запуск сервера и клиента

С вышеуказанными приготовлениями мы можем бежать. Сначала запустите сервер, затем запустите клиент.

Если проблем нет, он должен вывести следующее:

```
[nioEventLoopGroup-3-1] INFO  o.t.w.n.first.FirstServerHandler - Получил сообщение :Hello World

```

## Подведем итог

Мы сделали пример простой сервера и клиента. Подытожим рабочий процесс Netty. Для серверной части сначала установите 
обработчик (EventLoop) для фактической обработки сообщений, а затем используйте ServerBootstrap для группировки EventLoop и 
привязки порта к запуску. Для клиента также необходимо установить обработчик для обработки сообщения, 
затем вызвать Bootstrap для группировки EventLoop и привязать порт для запуска.

С учетом приведенного выше примера вы можете разработать свой собственный сервис NIO. Разве это не просто? 

Примеры этой статьи могут относиться к: [NettyLearning](https://github.com/WhiteK0T/Netty-Learning)