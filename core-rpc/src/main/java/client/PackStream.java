package client;

import message.Message;
import message.MessageIdGenerator;
import message.NotificationMessage;
import message.RequestMessage;
import message.ResponseMessage;
import message.SequentialMessageIdGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Two-way msgpack stream that wraps reading/writing bytes and exposes an interface for sending
 * {@link Message}
 *
 * <p>It is implemented by delegating reading to {@link RpcListener} and writing to {@link
 * RpcSender}, since {@link RpcSender} supports both of these functionalities. A single interface
 * makes it easier to communicate in a request/response model of communication.
 *
 * <p>Besides just passing down writing/reading, this class allows multiple {@link
 * RpcListener.RequestCallback} and multiple {@link RpcListener.NotificationCallback} by delegating
 * to internally held callbacks It also handles message id generation, meaning users of this class
 * don't have to manually set id's for messages Message id generation is handled by {@link
 * MessageIdGenerator} which can optionally be overriden through constructor. By default {@link
 * SequentialMessageIdGenerator} is used.
 *
 * <p>Sending messages supports any kind of {@link Message} and for {@link RequestMessage}, {@link
 * RpcListener.ResponseCallback} is supported, which will be called once corresponding {@link
 * ResponseMessage} arrives (message with same id as the request sent)
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Existing listener and sender
 * RpcStreamer rpcStreamer = new PackStream(sender, listener);
 * // Existing connection
 * rpcStreamer.attach(connection);
 *
 * rpcStreamer.send(message, (id, response) -> System.out.println(response)); // callback for request
 *
 * rpcStreamer.send(responseMessage); // Sending a response -> no need for a callback
 *
 * rpcStreamer.send(requestMessage); // Sending a request - fire and forget - no callback
 *
 * }</pre>
 *
 * <p>Example with custom id generator:
 *
 * <pre>{@code
 * // Create a generator
 * MessageIdGenerator generator = createCustomMessageIdGenerator();
 *
 * // Existing listener and sender
 * RpcStreamer rpcStreamer = new PackStream(sender, listener, generator);
 *
 * // ...
 *
 * }</pre>
 */
public final class PackStream implements RpcStreamer {
    public static final Logger log = LoggerFactory.getLogger(PackStream.class);

    private final RpcListener rpcListener;
    private final RpcSender rpcSender;
    private final MessageIdGenerator messageIdGenerator;

    private final List<RpcListener.RequestCallback> requestCallbacks = new ArrayList<>();
    private final List<RpcListener.NotificationCallback> notificationCallbacks = new ArrayList<>();

    /**
     * Creates a new {@link PackStream} with given {@link RpcSender} for sending messages and an
     * {@link RpcListener} for listening for incoming requests, responses and notifications Uses
     * {@link SequentialMessageIdGenerator} for {@link MessageIdGenerator}
     *
     * @param rpcSender {@link RpcSender} for sending data
     * @param rpcListener {@link RpcListener} for listening to incoming data
     * @throws NullPointerException if any parameter is null
     */
    public PackStream(RpcSender rpcSender, RpcListener rpcListener) {
        this(rpcSender, rpcListener, new SequentialMessageIdGenerator());
    }

    /**
     * Creates a new {@link PackStream} with given {@link RpcSender} for sending messages and an
     * {@link RpcListener} for listening for incoming requests, responses and notifications
     *
     * @param rpcSender {@link RpcSender} for sending data
     * @param rpcListener {@link RpcListener} for listening to incoming data
     * @param messageIdGenerator {@link MessageIdGenerator} for generating request message ids
     * @throws NullPointerException if any parameter is null
     */
    public PackStream(
            RpcSender rpcSender, RpcListener rpcListener, MessageIdGenerator messageIdGenerator) {
        Objects.requireNonNull(rpcSender, "rpcSender must be provided for two way communication");
        Objects.requireNonNull(
                rpcListener, "rpcListener must be provided for two way communication");
        Objects.requireNonNull(
                messageIdGenerator, "messageIdGenerator must be provided for sending requests");
        this.rpcListener = rpcListener;
        this.rpcSender = rpcSender;
        this.messageIdGenerator = messageIdGenerator;
    }

    /**
     * Prepares for writing to outgoing stream and prepares for reading from input stream Sets up
     * listeners on the input stream, so that current and any new request/response/notification
     * callbacks may be called - prepares the underlying {@link RpcListener} Also prepares for
     * writing messages - prepares the underlying {@link RpcSender}
     *
     * @throws NullPointerException if rpcConnection is null
     */
    @Override
    public void attach(RpcConnection rpcConnection) {
        Objects.requireNonNull(rpcConnection, "rpcConnection may not be null");
        log.info("Attaching PackStream to: {}", rpcConnection);
        startListening(rpcConnection.getIncomingStream());
        rpcSender.attach(rpcConnection.getOutgoingStream());
    }

    /**
     * Implemented per {@link RpcStreamer#send(Message)} specification Passes the message down to
     * underlying {@link RpcSender}, without callback
     */
    @Override
    public void send(Message message) throws IOException {
        log.debug("Sending message: {}", message);
        rpcSender.send(message);
    }

    /**
     * Implemented per {@link RpcStreamer#send(RequestMessage.Builder)} specification Passes the
     * message down to underlying {@link RpcSender}, without callback
     */
    @Override
    public void send(RequestMessage.Builder requestMessage) throws IOException {
        send(requestMessage, null);
    }

    /**
     * Implemented per {@link RpcStreamer#send(RequestMessage.Builder,
     * RpcListener.ResponseCallback)} specification Passes the message down to underlying {@link
     * RpcSender}, with callback First id for the message is generated using {@link
     * MessageIdGenerator}, callback for that id is prepared on {@link RpcListener} and then message
     * is send using {@link RpcSender}
     */
    @Override
    public void send(
            RequestMessage.Builder requestMessage, RpcListener.ResponseCallback responseCallback)
            throws IOException {
        var messageToSend = requestMessage.withId(messageIdGenerator.nextId()).build();
        rpcListener.listenForResponse(messageToSend.getId(), responseCallback);
        send(messageToSend);
    }

    /**
     * Adds a new {@link RpcListener.RequestCallback} per {@link
     * RpcStreamer#addRequestCallback(RpcListener.RequestCallback)} specification
     */
    @Override
    public void addRequestCallback(RpcListener.RequestCallback requestCallback) {
        log.info("Registered a new request callback: {}", requestCallback);
        if (!requestCallbacks.contains(requestCallback)) {
            this.requestCallbacks.add(requestCallback);
        }
    }

    /**
     * Removes a {@link RpcListener.RequestCallback} per {@link
     * RpcStreamer#removeRequestCallback(RpcListener.RequestCallback)} specification
     */
    @Override
    public void removeRequestCallback(RpcListener.RequestCallback requestCallback) {
        log.info("Removed a request callback: {}", requestCallback);
        if (requestCallbacks.contains(requestCallback)) {
            this.requestCallbacks.remove(requestCallback);
        }
    }

    /**
     * Adds a new {@link RpcListener.NotificationCallback} per {@link
     * RpcStreamer#addNotificationCallback(RpcListener.NotificationCallback)} specification
     */
    @Override
    public void addNotificationCallback(RpcListener.NotificationCallback notificationCallback) {
        log.info("Registered a new notification callback: {}", notificationCallback);
        if (!notificationCallbacks.contains(notificationCallback)) {
            this.notificationCallbacks.add(notificationCallback);
        }
    }

    /**
     * Removes a {@link RpcListener.NotificationCallback} per {@link
     * RpcStreamer#removeNotificationCallback(RpcListener.NotificationCallback)} specification
     */
    @Override
    public void removeNotificationCallback(RpcListener.NotificationCallback notificationCallback) {
        log.info("Removed a notification callback: {}", notificationCallback);
        if (notificationCallbacks.contains(notificationCallback)) {
            this.notificationCallbacks.remove(notificationCallback);
        }
    }

    /**
     * Stops the underlying {@link RpcListener} It is not expected for implementation to be reusable
     * after calling this method!
     */
    @Override
    public void stop() {
        log.info("Stopping resources");
        this.rpcListener.stop();
        this.rpcSender.stop();
    }

    private void requestReceived(RequestMessage requestMessage) {
        log.info("Request received: {}", requestMessage);
        for (var requestCallback : requestCallbacks) {
            requestCallback.requestReceived(requestMessage);
        }
    }

    private void notificationReceived(NotificationMessage notificationMessage) {
        log.info("Notification received: {}", notificationMessage);
        for (var notificationCallback : notificationCallbacks) {
            notificationCallback.notificationReceived(notificationMessage);
        }
    }

    private void startListening(InputStream inputStream) {
        rpcListener.listenForNotifications(this::notificationReceived);
        rpcListener.listenForRequests(this::requestReceived);
        rpcListener.start(inputStream);
    }
}
