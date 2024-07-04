package com.example.m_health.model.data


/**
 * Represents a collection of bounding box containing blood pressure value information.
 *
 * This data class defines the structure of a bounding box that contains information related to blood pressure values,
 * including the main value box, a list of digit boxes, the extracted numerical value, and the average confidence score.
 *
 * @param valueBox the bounding box containing the main blood pressure value information.
 * @param digitBoxes a mutable list of bounding boxes containing digit information within the value box.
 * @param value the extracted numerical value from the digit boxes (default value is 0).
 * @param avgConf the average confidence score associated with the value extraction (default value is 0F).
 */
data class BPValueBoundingBox(
    val valueBox: BoundingBox,
    val digitBoxes: MutableList<BoundingBox>,
    val value: Int = 0,
    val avgConf: Float = 0F
)