// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: nav_event.proto

package io.provlabs.flow.api;

public interface NavEventRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:nav.NavEventRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string denom = 1;</code>
   * @return The denom.
   */
  java.lang.String getDenom();
  /**
   * <code>string denom = 1;</code>
   * @return The bytes for denom.
   */
  com.google.protobuf.ByteString
      getDenomBytes();

  /**
   * <code>string scope_id = 2;</code>
   * @return The scopeId.
   */
  java.lang.String getScopeId();
  /**
   * <code>string scope_id = 2;</code>
   * @return The bytes for scopeId.
   */
  com.google.protobuf.ByteString
      getScopeIdBytes();

  /**
   * <code>int32 limit = 3;</code>
   * @return The limit.
   */
  int getLimit();
}
