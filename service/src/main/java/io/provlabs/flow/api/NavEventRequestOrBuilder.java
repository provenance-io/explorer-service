// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: nav_event.proto

package io.provlabs.flow.api;

public interface NavEventRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:nav.NavEventRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The token denomination to filter events by. Either `denom` or `scope_id` is required.
   * </pre>
   *
   * <code>string denom = 1;</code>
   * @return The denom.
   */
  java.lang.String getDenom();
  /**
   * <pre>
   * The token denomination to filter events by. Either `denom` or `scope_id` is required.
   * </pre>
   *
   * <code>string denom = 1;</code>
   * @return The bytes for denom.
   */
  com.google.protobuf.ByteString
      getDenomBytes();

  /**
   * <pre>
   * The scope ID to filter events by. Either `denom` or `scope_id` is required.
   * </pre>
   *
   * <code>string scope_id = 2;</code>
   * @return The scopeId.
   */
  java.lang.String getScopeId();
  /**
   * <pre>
   * The scope ID to filter events by. Either `denom` or `scope_id` is required.
   * </pre>
   *
   * <code>string scope_id = 2;</code>
   * @return The bytes for scopeId.
   */
  com.google.protobuf.ByteString
      getScopeIdBytes();

  /**
   * <pre>
   * Optional. A list of price denominations to filter by.
   * </pre>
   *
   * <code>repeated string price_denoms = 3;</code>
   * @return A list containing the priceDenoms.
   */
  java.util.List<java.lang.String>
      getPriceDenomsList();
  /**
   * <pre>
   * Optional. A list of price denominations to filter by.
   * </pre>
   *
   * <code>repeated string price_denoms = 3;</code>
   * @return The count of priceDenoms.
   */
  int getPriceDenomsCount();
  /**
   * <pre>
   * Optional. A list of price denominations to filter by.
   * </pre>
   *
   * <code>repeated string price_denoms = 3;</code>
   * @param index The index of the element to return.
   * @return The priceDenoms at the given index.
   */
  java.lang.String getPriceDenoms(int index);
  /**
   * <pre>
   * Optional. A list of price denominations to filter by.
   * </pre>
   *
   * <code>repeated string price_denoms = 3;</code>
   * @param index The index of the value to return.
   * @return The bytes of the priceDenoms at the given index.
   */
  com.google.protobuf.ByteString
      getPriceDenomsBytes(int index);

  /**
   * <pre>
   * Optional. The start date (in string format) to filter events.
   * </pre>
   *
   * <code>string from_date = 4;</code>
   * @return The fromDate.
   */
  java.lang.String getFromDate();
  /**
   * <pre>
   * Optional. The start date (in string format) to filter events.
   * </pre>
   *
   * <code>string from_date = 4;</code>
   * @return The bytes for fromDate.
   */
  com.google.protobuf.ByteString
      getFromDateBytes();

  /**
   * <pre>
   * Optional. The end date (in string format) to filter events.
   * </pre>
   *
   * <code>string to_date = 5;</code>
   * @return The toDate.
   */
  java.lang.String getToDate();
  /**
   * <pre>
   * Optional. The end date (in string format) to filter events.
   * </pre>
   *
   * <code>string to_date = 5;</code>
   * @return The bytes for toDate.
   */
  com.google.protobuf.ByteString
      getToDateBytes();

  /**
   * <pre>
   * Pagination details for the request
   * </pre>
   *
   * <code>.nav.PaginationRequest pagination = 6;</code>
   * @return Whether the pagination field is set.
   */
  boolean hasPagination();
  /**
   * <pre>
   * Pagination details for the request
   * </pre>
   *
   * <code>.nav.PaginationRequest pagination = 6;</code>
   * @return The pagination.
   */
  io.provlabs.flow.api.PaginationRequest getPagination();
  /**
   * <pre>
   * Pagination details for the request
   * </pre>
   *
   * <code>.nav.PaginationRequest pagination = 6;</code>
   */
  io.provlabs.flow.api.PaginationRequestOrBuilder getPaginationOrBuilder();
}
