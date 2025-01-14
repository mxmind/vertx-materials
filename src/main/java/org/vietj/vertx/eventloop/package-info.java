/**
 * = Demystifying the Event Loop
 * Julien Viet <julien@julienviet.com>
 *
 * The event loop plays an important role in Vert.x for writing highly scalable and performant network applications.
 *
 * The event loop is inherited from the Netty library on which Vert.x is based.
 *
 * We often use the expression _running on the event loop_, it has a very specific meaning: it means that the
 * current Thread is an event loop thread. This article provides an overview of the Vert.x event loop and the concepts
 * related to it.
 *
 * == The golden rule
 *
 * When using Vert.x there is one Vert.x golden rule to respect:
 *
 * [quote, Tim Fox]
 * Never block the event loop!
 *
 * The code executed on the event loop should never block the event loop, for instance:
 *
 * - using a blocking method directly or, for instance, reading a file with the `java.io.FileInputStream` api
 *   or a JDBC connection.
 * - doing a long and CPU intensive task
 *
 * When the event loop is blocked:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.BlockingEventLoop#main}
 * ----
 *
 * Vert.x will detect it and log a warn:
 *
 * ----
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 2616 ms time 2000000000
 * Apr 04, 2015 1:18:43 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 3617 ms time 2000000000
 * Apr 04, 2015 1:18:44 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 4619 ms time 2000000000
 * java.lang.Thread.sleep(Native Method)
 * Apr 04, 2015 1:18:45 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 5620 ms time 2000000000
 * io.vertx.example.BlockingEventLoop.start(BlockingEventLoop.java:19)
 * io.vertx.core.AbstractVerticle.start(AbstractVerticle.java:111)
 * io.vertx.core.impl.DeploymentManager.lambda$doDeploy$88(DeploymentManager.java:433)
 * io.vertx.core.impl.DeploymentManager$$Lambda$4/2141179775.handle(Unknown Source)
 * io.vertx.core.impl.ContextImpl.lambda$wrapTask$3(ContextImpl.java:263)
 * io.vertx.core.impl.ContextImpl$$Lambda$5/758013696.run(Unknown Source)
 * io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:380)
 * io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:357)
 * io.netty.util.concurrent.SingleThreadEventExecutor$2.run(SingleThreadEventExecutor.java:116)
 * java.lang.Thread.run(Thread.java:745)
 * ----
 *
 * The event loop must not be blocked, because it will freeze the parts of the applications using that event loop, with
 * severe consequences on the scalability and the throughput of the application.
 *
 * == The context
 *
 * Beyond the event loop, Vert.x defines the notion of a *_context_*. At a high level, the context can be thought of as
 * controlling the scope and order in which a set of handlers (or tasks created by handlers) are executed.
 *
 * When the Vert.x API _consumes_ callbacks (for instance setting an `HttpServer` request handler), it associates a callback handler
 * with a context. This context is then used for scheduling the callbacks, when such context is needed:
 *
 * - if the current thread is a Vert.x thread, it reuses the context associated with this thread: the context is propagated.
 * - otherwise a new context is created for this purpose.
 *
 * However there is one case where context propagation does not apply: deploying a Verticle creates a new context
 * for this Verticle, according to the deployment options of the deployment. Therefore a Verticle is always associated
 * with a context. Any handler registered within a verticle - whether it be an event bus consumer, HTTP server handler,
 * or any other asynchronous operation - will be registered using the verticle’s context.
 *
 * Vert.x provides three different types of contexts.
 *
 * - Event loop context
 * - Worker context
 * - Multi-threaded worker context
 *
 * === Event loop context
 *
 * An event loop context executes handlers on an event loop: handlers are executed directly on the IO threads, as
 * a consequence:
 *
 * - an handler will always be executed with the same thread
 * - an handler must never block the thread, otherwise it will create starvation for all the IO tasks associated
 * with that event loop.
 *
 * This behavior allows for a greatly simplified threading model by guaranteeing that associated handlers will
 * always be executed on the same thread, thus removing the need for synchronization and other locking mechanisms.
 *
 * This is the type of context that is the default and most commonly used type of context. Verticles deployed
 * without the worker flag will always be deployed with an event loop context.
 *
 * When Vert.x creates an event loop context, it chooses an event loop for this context, the event loop is chosen
 * via a round robin algorithm. This can be demonstrated by deploying the same verticle many times:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingManyEventLoops#main}
 * ----
 *
 * The result is:
 *
 * ----
 * Thread[main,5,main]
 * 0:Thread[vert.x-eventloop-thread-0,5,main]
 * 11:Thread[vert.x-eventloop-thread-11,5,main]
 * 10:Thread[vert.x-eventloop-thread-10,5,main]
 * 13:Thread[vert.x-eventloop-thread-13,5,main]
 * 12:Thread[vert.x-eventloop-thread-12,5,main]
 * 14:Thread[vert.x-eventloop-thread-14,5,main]
 * 16:Thread[vert.x-eventloop-thread-0,5,main]
 * 6:Thread[vert.x-eventloop-thread-6,5,main]
 * 15:Thread[vert.x-eventloop-thread-15,5,main]
 * 5:Thread[vert.x-eventloop-thread-5,5,main]
 * 4:Thread[vert.x-eventloop-thread-4,5,main]
 * 3:Thread[vert.x-eventloop-thread-3,5,main]
 * 2:Thread[vert.x-eventloop-thread-2,5,main]
 * 1:Thread[vert.x-eventloop-thread-1,5,main]
 * 17:Thread[vert.x-eventloop-thread-1,5,main]
 * 18:Thread[vert.x-eventloop-thread-2,5,main]
 * 19:Thread[vert.x-eventloop-thread-3,5,main]
 * 9:Thread[vert.x-eventloop-thread-9,5,main]
 * 8:Thread[vert.x-eventloop-thread-8,5,main]
 * 7:Thread[vert.x-eventloop-thread-7,5,main]
 * ----
 *
 * After sorting the result:
 *
 * ----
 * Thread[main,5,main]
 * 0:Thread[vert.x-eventloop-thread-0,5,main]
 * 1:Thread[vert.x-eventloop-thread-1,5,main]
 * 2:Thread[vert.x-eventloop-thread-2,5,main]
 * 3:Thread[vert.x-eventloop-thread-3,5,main]
 * 4:Thread[vert.x-eventloop-thread-4,5,main]
 * 5:Thread[vert.x-eventloop-thread-5,5,main]
 * 6:Thread[vert.x-eventloop-thread-6,5,main]
 * 7:Thread[vert.x-eventloop-thread-7,5,main]
 * 8:Thread[vert.x-eventloop-thread-8,5,main]
 * 9:Thread[vert.x-eventloop-thread-9,5,main]
 * 10:Thread[vert.x-eventloop-thread-10,5,main]
 * 11:Thread[vert.x-eventloop-thread-11,5,main]
 * 12:Thread[vert.x-eventloop-thread-12,5,main]
 * 13:Thread[vert.x-eventloop-thread-13,5,main]
 * 14:Thread[vert.x-eventloop-thread-14,5,main]
 * 15:Thread[vert.x-eventloop-thread-15,5,main]
 * 16:Thread[vert.x-eventloop-thread-0,5,main]
 * 17:Thread[vert.x-eventloop-thread-1,5,main]
 * 18:Thread[vert.x-eventloop-thread-2,5,main]
 * 19:Thread[vert.x-eventloop-thread-3,5,main]
 * ----
 *
 * As we can see we obtained different event loop threads for each Verticle and the thread are obtained with
 * a round robin policy. Note that the number of event loop threads by default depends on your CPU but this can
 * be configured.
 *
 * An event loop context guarantees to always use the same thread, however the converse is not true: the same thread
 * can be used by different event loop contexts. The previous example shows clearly that a same thread is used
 * for different event loops by the Round Robin policy.
 *
 * The default number of event loop created by a Vertx instance is twice the number of cores of your CPU. This value can
 * be overriden when creating a Vertx instance:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ConfigureThreadPool#eventLoop}
 * ----
 *
 * === Worker context
 *
 * Worker contexts are assigned to verticles deployed with the worker option enabled. The worker context is
 * differentiated from standard event loop contexts in that workers are executed on a separate worker thread pool.
 *
 * This separation from event loop threads allows worker contexts to execute the types of blocking operations that
 * will block the event loop: blocking such thread will not impact the application other than blocking one thread.
 *
 * Just as is the case with the event loop context, worker contexts ensure that handlers are only executed on one
 * thread at any given time. That is, handlers executed on a worker context will always be executed
 * sequentially - one after the other - but different actions may be executed on different threads.
 *
 * A common pattern is to deploy worker verticles and send them a message and then the worker replies to this message:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.WorkerReplying#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Executed by Thread[vert.x-worker-thread-1,5,main]
 * Executed by Thread[vert.x-worker-thread-2,5,main]
 * Executed by Thread[vert.x-worker-thread-3,5,main]
 * Executed by Thread[vert.x-worker-thread-4,5,main]
 * Executed by Thread[vert.x-worker-thread-5,5,main]
 * Executed by Thread[vert.x-worker-thread-6,5,main]
 * Executed by Thread[vert.x-worker-thread-7,5,main]
 * Executed by Thread[vert.x-worker-thread-8,5,main]
 * Executed by Thread[vert.x-worker-thread-9,5,main]
 * Executed by Thread[vert.x-worker-thread-10,5,main]
 * Executed by Thread[vert.x-worker-thread-11,5,main]
 * ----
 *
 * The previous example clearly shows that the worker context of the verticle use different worker threads
 * for delivering the messages:
 *
 * However the same thread can be used by several worker verticles:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.WorkerInstancesReplyingLowThreads#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Executed by worker 1 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 2 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 1 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 2 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 1 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 2 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 3 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 4 with Thread[vert.x-worker-thread-0,5,main]
 * Executed by worker 3 with Thread[vert.x-worker-thread-1,5,main]
 * Executed by worker 4 with Thread[vert.x-worker-thread-0,5,main]
 * ----
 *
 * The same worker verticle class can be deployed several times by specifying the number of instances. This allows
 * to concurrently process blocking tasks:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.WorkerInstancesReplying#main}
 * ----
 *
 * Workers can schedule timers:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.TimerOnWorkerThread#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Starting timer on Thread[vert.x-worker-thread-0,5,main]
 * Timer fired Thread[vert.x-worker-thread-1,5,main] after 1004 ms
 * ----
 *
 * Again the timer thread is not the same than the thread that created the timer.
 *
 * With a periodic timer:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.PeriodicOnWorkerThread#main}
 * ----
 *
 * we get a different thread for each event:
 *
 * ----
 * Starting periodic on Thread[vert.x-worker-thread-0,5,main]
 * Periodic fired Thread[vert.x-worker-thread-1,5,main] after 1004 ms
 * Periodic fired Thread[vert.x-worker-thread-2,5,main] after 2004 ms
 * Periodic fired Thread[vert.x-worker-thread-3,5,main] after 3004 ms
 * Periodic fired Thread[vert.x-worker-thread-4,5,main] after 4006 ms
 * Periodic fired Thread[vert.x-worker-thread-5,5,main] after 5004 ms
 * Periodic fired Thread[vert.x-worker-thread-6,5,main] after 6005 ms
 * Periodic fired Thread[vert.x-worker-thread-7,5,main] after 7004 ms
 * Periodic fired Thread[vert.x-worker-thread-8,5,main] after 8005 ms
 * Periodic fired Thread[vert.x-worker-thread-9,5,main] after 9005 ms
 * Periodic fired Thread[vert.x-worker-thread-10,5,main] after 10006 ms
 * Periodic fired Thread[vert.x-worker-thread-11,5,main] after 11006 ms
 * ----
 *
 * Since the worker thread may block, the delivery cannot be guaranteed in time:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.TimerOnWorkerThreadNotGuaranted#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Starting timer on Thread[vert.x-worker-thread-0,5,main]
 * Timer fired Thread[vert.x-worker-thread-0,5,main] after 2007 ms
 * ----
 *
 * Just like event loop, the size of the worker thread pool can be configured when creatin a Vertx instance:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ConfigureThreadPool#eventLoop}
 * ----
 *
 * === Multi-threaded worker context
 *
 * Multi-threaded contexts are assigned to verticles deployed with the multi-threaded option enabled. Whereas standard
 * worker contexts execute actions in order on a variety of threads, the multi-threaded worker context removes the
 * strong ordering of events to allow the execution of multiple events concurrently. This means that the user is
 * responsible for performing the appropriate concurrency control such as synchronization and locking.
 *
 * todo
 *
 * == Dealing with contexts
 *
 * Using a context is usually transparent, Vert.x will manage contexts implicitly when deploying a Verticle,
 * registering an Event Bus handler, etc... However the Vert.x API provides several ways to interact with a Context
 * allowing for manual context switching.
 *
 * === The current context
 *
 * The static `Vertx.currentContext()` methods returns the current context if there is one, it returns null otherwise.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CurrentContextFromMain#main}
 * ----
 *
 * We get obviously `null` no matter the Vertx instance we created before:
 *
 * ----
 * Current context is null
 * ----
 *
 * Now the same from a verticle leads to obtaining the `Verticle` context:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CurrentContextFromVerticle#main}
 * ----
 *
 * We get:
 *
 * ----
 * Current context is io.vertx.core.impl.EventLoopContext@424ff050
 * Verticle context is io.vertx.core.impl.EventLoopContext@424ff050
 * ----
 *
 * === Creating or reusing a context
 *
 * The `vertx.getOrCreateContext()` method returns the context associated with the current thread (like `currentContext`)
 * otherwise it creates a new context, associates it to an event loop and returns it:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.GettingOrCreatingContextFromMain#main}
 * ----
 *
 * Note, that creating a context, will not associate the current thread with this context. This will indeed not
 * change the nature of the current thread! However we can now use this context for running an action:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingAndUsingContextFromMain#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Current context is io.vertx.core.impl.EventLoopContext@17979104
 * ----
 *
 * Calling `getOrCreateContext` from a verticle returns the context associated with the Verticle:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.GettingOrCreatingContextFromVerticle#main}
 * ----
 *
 * This prints:
 *
 * ----
 * io.vertx.core.impl.EventLoopContext@10b02dc5
 * io.vertx.core.impl.EventLoopContext@10b02dc5
 * ----
 *
 * === Running on context
 *
 * The `io.vertx.core.Context.runOnContext(Handler)` method can be used when the thread attached to the context needs
 * to run a particular task on a context.
 *
 * For instance, the context thread initiates a non Vert.x action, when this action ends it needs to do update some
 * state and it needs to be done with the context thread to guarantee that the state will be visible by the
 * context thread.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.RunningOnContext#start()}
 * ----
 *
 * This prints:
 *
 * ----
 * Running with context : io.vertx.core.impl.EventLoopContext@69cdd6d8
 * Current context : null
 * Runs on the original context : io.vertx.core.impl.EventLoopContext@69cdd6d8
 * ----
 *
 * The `vertx.runOnContext(Handler<Void>)` is a shortcut for what we have seen before: it calls the
 * `getOrCreateContext` method and schedule a task for execution via the `context.runOnContext(Handler<Void>)` method.
 *
 * === Blocking
 *
 * Before Vert.x 3, using blocking API required to deploy a worker Verticle. Vert.x 3 provides an additional API
 * for using a blocking API:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingSuccess#execute}
 * ----
 *
 * This prints:
 *
 * ----
 * Calling blocking block from Thread[vert.x-eventloop-thread-0,5,main]
 * Computing with Thread[vert.x-worker-thread-0,5,main]
 * Got result in Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * While the blocking action executes with a worker thread, the result handler is executed with the same event
 * loop context.
 *
 * The blocking action is provided a `Future` argument that is used for signaling when the result is obtained,
 * usually a result of the blocking API.
 *
 * When the blocking action fails the result handler will get the failure as cause of the async result object:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingThrowingFailure#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Blocking code failed
 * java.lang.RuntimeException
 * at org.vietj.vertx.eventloop.ExecuteBlockingThrowingFailure.lambda$null$0(ExecuteBlockingThrowingFailure.java:19)
 * at org.vietj.vertx.eventloop.ExecuteBlockingThrowingFailure$$Lambda$4/163784093.handle(Unknown Source)
 * at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$2(ContextImpl.java:217)
 * at io.vertx.core.impl.ContextImpl$$Lambda$6/1645685573.run(Unknown Source)
 * at io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor.lambda$new$180(OrderedExecutorFactory.java:91)
 * at io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor$$Lambda$2/1053782781.run(Unknown Source)
 * at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
 * at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
 * at java.lang.Thread.run(Thread.java:745)
 * ----
 *
 * The blocking action can also report the failure on the `Future` object:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingFailingFuture#main}
 * ----
 *
 * Obviously executing a task from the blocking action on the context will use the event loop:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.ExecuteBlockingRunOnContext#execute}
 * ----
 *
 * Which outputs:
 *
 * ----
 * Calling blocking block from Thread[vert.x-eventloop-thread-0,5,main]
 * Computing with Thread[vert.x-worker-thread-0,5,main]
 * Running on context from the worker Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * This API is somewhat similar to deploying a worker Verticle, although its purpose is to execute a single
 * blocking operation from an event loop context.
 *
 * CAUTION: while the `executeBlocking` is a `Vertx` method, the blocking actions are scheduled on the underlying
 * context and serialized, i.e executed one after another and not in parallel.
 *

 Execute blocking for any particular verticle instance uses the same context as that instance.

 If you call executeBlocking multiple times in any particular instance they will be executed in the order you called them. If we didn't do that you'd get into a mess, e.g. if you did an insertBlocking to insert some data into a table, followed by another to select from that table, then there'd be no guarantee in which order they occurred so you might not find your data.

 *
 * === Determining the kind of context
 *
 * The kind of a context can be determined with the methods:
 *
 * - `Context#isEventLoopContext`
 * - `Context#isWorkerContext`
 * - `Context#isMultiThreadedWorkerContext`
 *
 * WARNING: the nature of the context does not guarantee the nature of the thread, indeed the `executeBlocking`
 * method can execute a task with a worker thread in an event loop context
 *
 * === Determining the kind of thread
 *
 * As said earlier, the nature of the context impacts the concurrency. The `executeBlocking` method can even change
 * use a worker thread in an event loop context. The kind of context should be properly determined with the static methods:
 *
 * - `Context#isOnEventLoopThread()`
 * - `Context#isOnWorkerThread()`
 *
 * === Concurrency
 *
 * When the Vert.x API needs a context, it calls the `vertx.getOrCreateContext()` method, when the Vert.x API is used
 * in a context, for instance when deploying a Verticle. This implies that any service created from this Verticle
 * will reuse the same context, for instance:
 *
 * - Creating a server
 * - Creating a client
 * - Creating a timer
 * - Registering an event but handler
 * - etc...
 *
 * Such _services_ will call back the Verticle that created them at some point, how this happens is according
 * to the context: the context remains the same, however its nature has a direct impact on the concurrency as it
 * govers the threading model:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.SharingStateInContext#eventLoop}
 * ----
 *
 * Deployed as a worker, it needs to use synchronization, pretty much like this:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.SharingStateInContext#worker}
 * ----
 *
 * == Embedding Vert.x
 *
 * When Vert.x is embedded like in a _main_ Java method or a _junit_ test, the thread creating Vert.x can be any kind of thread, but
 * it is certainly not a Vert.x thread. Any action that requires a context will implicitly create an event loop context for
 * executing this action.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingAnEventLoopFromHttpServer#main}
 * ----
 *
 * When several actions are done, there will use different context and there are high chances they will use a
 * different event loop thread.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingDifferentEventLoopsFromHttpServers#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Current thread is Thread[vert.x-eventloop-thread-1,5,main]
 * Current thread is Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * Therefore accessing a shared state from both servers should not be done!
 *
 * When the same context needs to be used then the actions can be grouped with a `runOnContext` call:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.UsingEventLoopsFromHttpServers#main}
 * ----
 *
 * This prints:
 *
 * ----
 * Current thread is Thread[vert.x-eventloop-thread-0,5,main]
 * Current thread is Thread[vert.x-eventloop-thread-0,5,main]
 * ----
 *
 * Now we can share state between the two servers safely.
 *
 * == Vert.x Core apis
 *
 * Vert.x API consumes handlers and assign them to context, this section provides a quick overview of the Vert.x
 * Core APIs.
 *
 * === TCP Servers
 *
 * TCP servers (HttpServer and NetServer) can run with both event loop and worker contexts. A TCP server consumes
 * a context for the various handlers it uses.
 *
 * A worker server uses under the hood an event loop for its IO operations, however the worker context is used
 * for calling the registered handlers. Consequently a worker server can block directly, when it happens, this will
 * not have consequences on the underlying event loop, however it does impact directly the server, as this particular
 * server will be blocked: of course the server can be scaled to many workers to handle multiple blocking requests
 * concurrently, this is the classic multithreaded server model.
 *
 * === TCP Clients
 *
 * TCP clients (HttpClient and NetClient) can run with both event loop and worker contexts. Clients don't have a particular
 * context assigned. A context is instead assigned every time a connection or a request is done.
 *
 * === Timers
 *
 * Every time a timer or periodic is created, a context is assigned, this context is then used when the timer or
 * periodic fires. Event bus or worker contexts are allowed.
 *
 * === Event bus
 *
 * A context is assigned when an handler is registered for consuming a message, it can be a registered consumer
 * or registering a message reply handler. Event bus or worker contexts are allowed.
 */
@Document(fileName = "Demystifying_the_event_loop.adoc")
package org.vietj.vertx.eventloop;

import io.vertx.docgen.Document;