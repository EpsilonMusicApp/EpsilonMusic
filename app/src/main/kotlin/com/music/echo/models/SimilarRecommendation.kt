

package echo.music.***REMOVED***.models

import com.music.innertube.models.YTItem
import echo.music.***REMOVED***.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
