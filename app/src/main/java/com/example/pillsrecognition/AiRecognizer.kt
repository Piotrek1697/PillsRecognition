package com.example.pillsrecognition

class AiRecognizer(var label: String, var probality: Float, var tag: Int) {
    override fun toString(): String {
        return "label='$label', probality=$probality, tag=$tag"
    }
}