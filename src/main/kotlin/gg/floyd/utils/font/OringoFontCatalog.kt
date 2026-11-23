package gg.floyd.utils.font

import java.nio.file.Files
import java.nio.file.Path

/** Font faces and sizes from the user's Oringo 1.7.1 Fonts.bootstrap reference. */
object OringoFontCatalog {
    data class Face(val id:String,val label:String,val fileName:String,val resource:String?=null,val systemFile:String?=null)
    data class Preset(val id:String,val label:String,val face:String,val size:Double)
    val faces=listOf(
        Face("roboto","Roboto Regular","oringo-roboto-regular.ttf","/assets/floydaddons/fonts/oringo/Roboto-Regular.ttf"),
        Face("roboto-medium","Roboto Medium","oringo-roboto-medium.ttf","/assets/floydaddons/fonts/oringo/Roboto-Medium.ttf"),
        Face("tahoma","Tahoma","oringo-tahoma.ttf",systemFile="tahoma.ttf"),
        Face("tahoma-bold","Tahoma Bold","oringo-tahoma-bold.ttf",systemFile="tahomabd.ttf"))
    val presets=listOf(
        Preset("roboto-small","Roboto Small","roboto",18.0),
        Preset("roboto-medium","Roboto Medium","roboto",19.0),
        Preset("roboto-big","Roboto Big","roboto-medium",20.0),
        Preset("roboto-medium-bold","Roboto Medium Bold","roboto-medium",19.0),
        Preset("tahoma","Tahoma","tahoma",22.0),
        Preset("tahoma-bold","Tahoma Bold","tahoma-bold",22.0),
        // Oringo's tahomaSmall field is an alias of robotoMediumBold, not a Tahoma file.
        Preset("tahoma-small","Tahoma Small","roboto-medium",19.0),
        Preset("name-tag","Name Tag","roboto-medium",38.0))
    fun face(preset:Preset)=faces.first { it.id==preset.face }
    fun systemFont(face:Face):Path? {
        val file=face.systemFile ?: return null
        val directories=buildList {
            System.getenv("WINDIR")?.let { add(Path.of(it,"Fonts")) }
            System.getenv("LOCALAPPDATA")?.let { add(Path.of(it,"Microsoft","Windows","Fonts")) }
            add(Path.of("/usr/share/fonts/truetype/msttcorefonts"))
        }
        return directories.map { it.resolve(file) }.firstOrNull { Files.isRegularFile(it) && Files.isReadable(it) }
    }
    fun available(preset:Preset,directory:Path):Boolean {
        val face=face(preset)
        return Files.isRegularFile(directory.resolve(face.fileName)) || face.resource?.let { javaClass.getResource(it)!=null }==true || systemFont(face)!=null
    }
    /** Only fixed catalog filenames are created. Existing user files are never overwritten. */
    @Synchronized fun materialize(preset:Preset,directory:Path):Path? { return runCatching {
        val face=face(preset);Files.createDirectories(directory)
        val target=directory.resolve(face.fileName)
        if(!Files.isRegularFile(target)) {
            if(face.resource!=null) {
                val stream=javaClass.getResourceAsStream(face.resource) ?: return null
                stream.use { Files.copy(it,target) }
            } else Files.copy(systemFont(face) ?: return null,target)
        }
        target.takeIf { Files.isReadable(it) }
    }.getOrNull() }
}
