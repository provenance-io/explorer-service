object Platform {
    object OS {
        val name: String
            get() = System.getProperty("os.name")
        val arch: String
            get() = System.getProperty("os.arch")
        val isAppleSilicon: Boolean
            get() = name == "Mac OS X" && arch == "aarch64"
    }

    val availableProcessors: Int
        get() = Runtime().availableProcessors()
}

fun Runtime(): Runtime = Runtime.getRuntime()
