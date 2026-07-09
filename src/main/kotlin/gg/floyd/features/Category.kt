package gg.floyd.features

import gg.floyd.features.Category.Companion.categories

@ConsistentCopyVisibility
data class Category private constructor(val name: String) {
    companion object {

        /**
         * Map containing all the categories, with the key being the name.
         */
        val categories: LinkedHashMap<String, Category> = linkedMapOf()

        @JvmField
        val RENDER = custom(name = "Render")
        @JvmField
        val HIDERS = custom(name = "Hiders")
        @JvmField
        val PLAYER = custom(name = "Player")
        @JvmField
        val CAMERA = custom(name = "Camera")
        @JvmField
        val COSMETIC = custom(name = "Cosmetic")
        @JvmField
        val PVP = custom(name = "QoL")
        @JvmField
        val MISC = custom(name = "Misc")

        /**
         * Returns a category with name provided.
         *
         * If a category with the same name has already been made, it won't reallocate.
         * Otherwise, it will be added to [categories].
         */
        fun custom(name: String): Category {
            return categories.getOrPut(name) { Category(name) }
        }
    }
}
