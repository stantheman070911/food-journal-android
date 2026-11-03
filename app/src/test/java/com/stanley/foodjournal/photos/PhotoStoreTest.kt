package com.stanley.foodjournal.photos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    private fun saved(name: String): String {
        store.photoFile(name).writeText("photo")
        store.thumbFile(name).writeText("thumb")
        return name
    }
}
