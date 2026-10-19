package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AvatarDisplay(avatarId: Int, modifier: Modifier = Modifier) {
    val bitmap = ImageBitmap.imageResource(id = R.drawable.avatar_grid)
    
    val rows = 4
    val cols = 5
    
    val avatarWidth = bitmap.width / cols
    val avatarHeight = bitmap.height / rows
    
    val row = avatarId / cols
    val col = avatarId % cols
    
    Canvas(modifier = modifier) {
        // Draw the specific part of the grid
        val srcRect = android.graphics.Rect(col * avatarWidth, row * avatarHeight, (col + 1) * avatarWidth, (row + 1) * avatarHeight)
        val dstRect = android.graphics.Rect(0, 0, size.width.toInt(), size.height.toInt())
        
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(bitmap.asAndroidBitmap(), srcRect, dstRect, null)
        }
    }
}
