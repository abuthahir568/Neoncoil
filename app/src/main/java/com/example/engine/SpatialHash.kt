package com.example.engine

import kotlin.math.floor

/**
 * Allocation-light 2D Spatial Hash Grid for fast O(1) neighborhood queries
 * across hundreds of food items and thousands of snake body segments.
 */
class SpatialHash<T>(private val cellSize: Float = 160f) {
    @PublishedApi
    internal val buckets = HashMap<Long, MutableList<T>>(256)
    private val listPool = ArrayList<MutableList<T>>(256)

    fun clear() {
        for (list in buckets.values) {
            list.clear()
            if (listPool.size < 512) {
                listPool.add(list)
            }
        }
        buckets.clear()
    }

    fun insert(x: Float, y: Float, item: T) {
        val key = packCell(cellCoord(x), cellCoord(y))
        val bucket = buckets.getOrPut(key) {
            if (listPool.isNotEmpty()) listPool.removeAt(listPool.lastIndex) else ArrayList(8)
        }
        bucket.add(item)
    }

    inline fun queryRadius(
        x: Float,
        y: Float,
        radius: Float,
        action: (T) -> Unit
    ) {
        val minCx = cellCoord(x - radius)
        val maxCx = cellCoord(x + radius)
        val minCy = cellCoord(y - radius)
        val maxCy = cellCoord(y + radius)
        for (cx in minCx..maxCx) {
            for (cy in minCy..maxCy) {
                val list = buckets[packCell(cx, cy)] ?: continue
                for (i in 0 until list.size) {
                    action(list[i])
                }
            }
        }
    }

    @PublishedApi
    internal fun cellCoord(v: Float): Int = floor(v / cellSize).toInt()

    @PublishedApi
    internal fun packCell(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
}

class SegmentRef(
    var snake: SnakeEntity,
    var segIndex: Int,
    var x: Float,
    var y: Float,
    var radius: Float
)
