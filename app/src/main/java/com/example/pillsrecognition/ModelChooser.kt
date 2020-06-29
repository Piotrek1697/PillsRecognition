package com.example.pillsrecognition

enum class ModelChooser(var aiLabel : String, var modelPath : Int?) {
    NO_SPA("no_spa", R.raw.nospa),
    IBURAPID("Iburapid", R.raw.nomodel),
    CALPEROS("calperos", R.raw.calperos),
    IBUPROM_RR("Ibuprom_rr", R.raw.ibuprom),
    FUROXIN("Furoxin",R.raw.furoxin),
    SCORBOLAMID("Scorbolamid",R.raw.scorbolamid),
    DEFAULT("No", R.raw.nomodel);

    companion object{
        fun parseString(label: String): ModelChooser {
            when (label) {
                "no_spa" -> return NO_SPA
                "Iburapid" -> return IBURAPID
                "calperos" -> return CALPEROS
                "Ibuprom_rr" -> return  IBUPROM_RR
                "Furoxin" -> return  FUROXIN
                "Scorbolamid" -> return SCORBOLAMID
            }
            return DEFAULT
        }
    }

}

