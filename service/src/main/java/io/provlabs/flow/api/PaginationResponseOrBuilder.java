// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: nav_event.proto

package io.provlabs.flow.api;

public interface PaginationResponseOrBuilder extends
    // @@protoc_insertion_point(interface_extends:nav.PaginationResponse)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>int32 current_page = 1;</code>
   * @return The currentPage.
   */
  int getCurrentPage();

  /**
   * <code>int32 total_pages = 2;</code>
   * @return The totalPages.
   */
  int getTotalPages();

  /**
   * <code>int32 total_items = 3;</code>
   * @return The totalItems.
   */
  int getTotalItems();
}
