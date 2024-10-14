package io.provlabs.flow.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 * <pre>
 * NavService is the gRPC service for retrieving NavEvents.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler",
    comments = "Source: nav_event.proto")
public final class NavServiceGrpc {

  private NavServiceGrpc() {}

  public static final String SERVICE_NAME = "nav.NavService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.provlabs.flow.api.NavEventRequest,
      io.provlabs.flow.api.NavEventResponse> getGetNavEventsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetNavEvents",
      requestType = io.provlabs.flow.api.NavEventRequest.class,
      responseType = io.provlabs.flow.api.NavEventResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.provlabs.flow.api.NavEventRequest,
      io.provlabs.flow.api.NavEventResponse> getGetNavEventsMethod() {
    io.grpc.MethodDescriptor<io.provlabs.flow.api.NavEventRequest, io.provlabs.flow.api.NavEventResponse> getGetNavEventsMethod;
    if ((getGetNavEventsMethod = NavServiceGrpc.getGetNavEventsMethod) == null) {
      synchronized (NavServiceGrpc.class) {
        if ((getGetNavEventsMethod = NavServiceGrpc.getGetNavEventsMethod) == null) {
          NavServiceGrpc.getGetNavEventsMethod = getGetNavEventsMethod =
              io.grpc.MethodDescriptor.<io.provlabs.flow.api.NavEventRequest, io.provlabs.flow.api.NavEventResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetNavEvents"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.provlabs.flow.api.NavEventRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.provlabs.flow.api.NavEventResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NavServiceMethodDescriptorSupplier("GetNavEvents"))
              .build();
        }
      }
    }
    return getGetNavEventsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.provlabs.flow.api.LatestNavEventRequest,
      io.provlabs.flow.api.NavEventResponse> getGetLatestNavEventsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetLatestNavEvents",
      requestType = io.provlabs.flow.api.LatestNavEventRequest.class,
      responseType = io.provlabs.flow.api.NavEventResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.provlabs.flow.api.LatestNavEventRequest,
      io.provlabs.flow.api.NavEventResponse> getGetLatestNavEventsMethod() {
    io.grpc.MethodDescriptor<io.provlabs.flow.api.LatestNavEventRequest, io.provlabs.flow.api.NavEventResponse> getGetLatestNavEventsMethod;
    if ((getGetLatestNavEventsMethod = NavServiceGrpc.getGetLatestNavEventsMethod) == null) {
      synchronized (NavServiceGrpc.class) {
        if ((getGetLatestNavEventsMethod = NavServiceGrpc.getGetLatestNavEventsMethod) == null) {
          NavServiceGrpc.getGetLatestNavEventsMethod = getGetLatestNavEventsMethod =
              io.grpc.MethodDescriptor.<io.provlabs.flow.api.LatestNavEventRequest, io.provlabs.flow.api.NavEventResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetLatestNavEvents"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.provlabs.flow.api.LatestNavEventRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.provlabs.flow.api.NavEventResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NavServiceMethodDescriptorSupplier("GetLatestNavEvents"))
              .build();
        }
      }
    }
    return getGetLatestNavEventsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static NavServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NavServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NavServiceStub>() {
        @java.lang.Override
        public NavServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NavServiceStub(channel, callOptions);
        }
      };
    return NavServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static NavServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NavServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NavServiceBlockingStub>() {
        @java.lang.Override
        public NavServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NavServiceBlockingStub(channel, callOptions);
        }
      };
    return NavServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static NavServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NavServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NavServiceFutureStub>() {
        @java.lang.Override
        public NavServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NavServiceFutureStub(channel, callOptions);
        }
      };
    return NavServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * NavService is the gRPC service for retrieving NavEvents.
   * </pre>
   */
  public static abstract class NavServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Retrieves NavEvents based on denom, scope, and optional filters.
     * </pre>
     */
    public void getNavEvents(io.provlabs.flow.api.NavEventRequest request,
        io.grpc.stub.StreamObserver<io.provlabs.flow.api.NavEventResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getGetNavEventsMethod(), responseObserver);
    }

    /**
     * <pre>
     * Retrieves the latest NavEvents based on price denomination and marker/scope inclusion.
     * </pre>
     */
    public void getLatestNavEvents(io.provlabs.flow.api.LatestNavEventRequest request,
        io.grpc.stub.StreamObserver<io.provlabs.flow.api.NavEventResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getGetLatestNavEventsMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getGetNavEventsMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                io.provlabs.flow.api.NavEventRequest,
                io.provlabs.flow.api.NavEventResponse>(
                  this, METHODID_GET_NAV_EVENTS)))
          .addMethod(
            getGetLatestNavEventsMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                io.provlabs.flow.api.LatestNavEventRequest,
                io.provlabs.flow.api.NavEventResponse>(
                  this, METHODID_GET_LATEST_NAV_EVENTS)))
          .build();
    }
  }

  /**
   * <pre>
   * NavService is the gRPC service for retrieving NavEvents.
   * </pre>
   */
  public static final class NavServiceStub extends io.grpc.stub.AbstractAsyncStub<NavServiceStub> {
    private NavServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NavServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NavServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Retrieves NavEvents based on denom, scope, and optional filters.
     * </pre>
     */
    public void getNavEvents(io.provlabs.flow.api.NavEventRequest request,
        io.grpc.stub.StreamObserver<io.provlabs.flow.api.NavEventResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getGetNavEventsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Retrieves the latest NavEvents based on price denomination and marker/scope inclusion.
     * </pre>
     */
    public void getLatestNavEvents(io.provlabs.flow.api.LatestNavEventRequest request,
        io.grpc.stub.StreamObserver<io.provlabs.flow.api.NavEventResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getGetLatestNavEventsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * NavService is the gRPC service for retrieving NavEvents.
   * </pre>
   */
  public static final class NavServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<NavServiceBlockingStub> {
    private NavServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NavServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NavServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Retrieves NavEvents based on denom, scope, and optional filters.
     * </pre>
     */
    public io.provlabs.flow.api.NavEventResponse getNavEvents(io.provlabs.flow.api.NavEventRequest request) {
      return blockingUnaryCall(
          getChannel(), getGetNavEventsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Retrieves the latest NavEvents based on price denomination and marker/scope inclusion.
     * </pre>
     */
    public io.provlabs.flow.api.NavEventResponse getLatestNavEvents(io.provlabs.flow.api.LatestNavEventRequest request) {
      return blockingUnaryCall(
          getChannel(), getGetLatestNavEventsMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * NavService is the gRPC service for retrieving NavEvents.
   * </pre>
   */
  public static final class NavServiceFutureStub extends io.grpc.stub.AbstractFutureStub<NavServiceFutureStub> {
    private NavServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NavServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NavServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Retrieves NavEvents based on denom, scope, and optional filters.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.provlabs.flow.api.NavEventResponse> getNavEvents(
        io.provlabs.flow.api.NavEventRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getGetNavEventsMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Retrieves the latest NavEvents based on price denomination and marker/scope inclusion.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.provlabs.flow.api.NavEventResponse> getLatestNavEvents(
        io.provlabs.flow.api.LatestNavEventRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getGetLatestNavEventsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_NAV_EVENTS = 0;
  private static final int METHODID_GET_LATEST_NAV_EVENTS = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final NavServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(NavServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_NAV_EVENTS:
          serviceImpl.getNavEvents((io.provlabs.flow.api.NavEventRequest) request,
              (io.grpc.stub.StreamObserver<io.provlabs.flow.api.NavEventResponse>) responseObserver);
          break;
        case METHODID_GET_LATEST_NAV_EVENTS:
          serviceImpl.getLatestNavEvents((io.provlabs.flow.api.LatestNavEventRequest) request,
              (io.grpc.stub.StreamObserver<io.provlabs.flow.api.NavEventResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class NavServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    NavServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.provlabs.flow.api.NavEventOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("NavService");
    }
  }

  private static final class NavServiceFileDescriptorSupplier
      extends NavServiceBaseDescriptorSupplier {
    NavServiceFileDescriptorSupplier() {}
  }

  private static final class NavServiceMethodDescriptorSupplier
      extends NavServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    NavServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (NavServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new NavServiceFileDescriptorSupplier())
              .addMethod(getGetNavEventsMethod())
              .addMethod(getGetLatestNavEventsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
