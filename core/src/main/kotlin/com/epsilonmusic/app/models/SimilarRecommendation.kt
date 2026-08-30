

package com.epsilonmusic.app.models

import com.music.innertube.models.YTItem
import com.epsilonmusic.app.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
