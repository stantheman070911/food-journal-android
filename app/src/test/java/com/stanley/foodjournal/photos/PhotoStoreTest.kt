package com.stanley.foodjournal.photos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class PhotoStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { PhotoStore(folder.root).also { it.ensureFolders() } }

    @Test
    fun `every photo gets a new name`() {
        assertNotEquals(store.newName(), store.newName())
    }

    @Test
    fun `commit moves the photo and its thumbnail into place`() = runBlocking {
        val name = store.newName()
        store.pendingPhotoFile(name).writeText("photo")
        store.pendingThumbFile(name).writeText("thumb")

        store.commit(name)

        assertEquals("photo", store.photoFile(name).readText())
        assertEquals("thumb", store.thumbFile(name).readText())
        assertFalse(store.pendingPhotoFile(name).exists())
        assertFalse(store.pendingThumbFile(name).exists())
    }

    @Test
    fun `discarding removes every pending file of a photo`() = runBlocking {
        val name = store.newName()
        listOf(store.pendingPhotoFile(name), store.pendingThumbFile(name), store.captureFile(name))
            .forEach { it.writeText("x") }

        store.discardPending(name)

        assertFalse(store.pendingPhotoFile(name).exists())
        assertFalse(store.pendingThumbFile(name).exists())
        assertFalse(store.captureFile(name).exists())
    }

    @Test
    fun `sweep removes saved photos no meal uses and keeps the rest`() = runBlocking {
        val used = saved(store.newName())
        val unused = saved(store.newName())

        store.sweep(inUse = setOf(used))

        assertTrue(store.photoFile(used).exists())
        assertTrue(store.thumbFile(used).exists())
        assertFalse(store.photoFile(unused).exists())
        assertFalse(store.thumbFile(unused).exists())
    }

    @Test
    fun `sweep keeps recent pending photos and removes week-old ones`() = runBlocking {
        val now = System.currentTimeMillis()
        val recent = store.pendingPhotoFile(store.newName()).also { it.writeText("x") }
        val old = store.pendingPhotoFile(store.newName()).also {
            it.writeText("x")
            it.setLastModified(now - TimeUnit.DAYS.toMillis(8))
        }

        store.sweep(inUse = emptySet(), nowMillis = now)

        assertTrue(recent.exists())
        assertFalse(old.exists())
    }

    @Test
    fun `swapping in an import keeps the old photos aside until they are dropped`() = runBlocking {
        val old = saved(store.newName())
        val incoming = File(store.importFolder, "photos").apply { mkdirs() }
        val new = store.newName().also { File(incoming, it).writeText("new") }

        store.swapIn(incoming)

        assertTrue(store.photoFile(new).exists())
        assertFalse(store.photoFile(old).exists())
        store.dropOldPhotos()
        assertFalse(File(folder.root, "photos-old").exists())
    }

    @Test
    fun `swapping back brings the old photos back`() = runBlocking {
        val old = saved(store.newName())
        val incoming = File(store.importFolder, "photos").apply { mkdirs() }
        val new = store.newName().also { File(incoming, it).writeText("new") }

        store.swapIn(incoming)
        store.swapBack()

        assertEquals("photo", store.photoFile(old).readText())
        assertFalse(store.photoFile(new).exists())
    }

    @Test
    fun `an interrupted import keeps both sets of photos for the sweep to sort out`() = runBlocking {
        val old = saved(store.newName())
        val shared = saved(store.newName())
        val incoming = File(store.importFolder, "photos").apply { mkdirs() }
        val new = store.newName().also { File(incoming, it).writeText("new") }
        File(incoming, shared).writeText("photo")
        store.swapIn(incoming)
        File(store.importFolder, "backup.json").writeText("{}")

        store.recoverInterruptedImport()

        assertTrue(store.photoFile(old).exists())
        assertTrue(store.photoFile(shared).exists())
        assertTrue(store.photoFile(new).exists())
        assertFalse(File(folder.root, "photos-old").exists())
        assertFalse(store.importFolder.exists())
    }

    @Test
    fun `an import interrupted between the two renames loses nothing`() = runBlocking {
        val old = saved(store.newName())
        File(folder.root, "photos").renameTo(File(folder.root, "photos-old"))

        store.recoverInterruptedImport()

        assertEquals("photo", store.photoFile(old).readText())
    }

    private fun saved(name: String): String {
        store.photoFile(name).writeText("photo")
        store.thumbFile(name).writeText("thumb")
        return name
    }
}
