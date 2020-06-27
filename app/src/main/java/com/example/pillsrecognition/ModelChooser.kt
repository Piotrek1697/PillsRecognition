package com.example.pillsrecognition

enum class ModelChooser(var aiLabel : String, var modelPath : Int?) {
    NO_SPA("no_spa", R.raw.nospa),
    IBURAPID("Iburapid", R.raw.calperos),
    DEFAULT("No", null);

    companion object{
        fun parseString(label: String): ModelChooser {
            when (label) {
                "no_spa" -> return NO_SPA
                "Iburapid" -> return IBURAPID
            }
            return DEFAULT
        }
    }

}

