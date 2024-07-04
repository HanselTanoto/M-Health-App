package com.example.m_health.model.data


/**
 * Represents a bounding box in an object detection context.
 *
 * This data class defines the structure of a bounding box used in object detection tasks. It includes properties such as
 * the coordinates of the top-left and bottom-right corners, center coordinates, width, height, confidence score, class index,
 * and class name associated with the detected object.
 *
 * @param x1 the x-coordinate of the top-left corner of the bounding box.
 * @param y1 the y-coordinate of the top-left corner of the bounding box.
 * @param x2 the x-coordinate of the bottom-right corner of the bounding box.
 * @param y2 the y-coordinate of the bottom-right corner of the bounding box.
 * @param cx the x-coordinate of the center of the bounding box.
 * @param cy the y-coordinate of the center of the bounding box.
 * @param w the width of the bounding box.
 * @param h the height of the bounding box.
 * @param conf the confidence score associated with the detected object.
 * @param cls the class index of the detected object.
 * @param clsName the class name associated with the detected object.
 */
data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val conf: Float,
    val cls: Int,
    val clsName: String
)