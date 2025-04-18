package one.mixin.android.inscription.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.transformations
import one.mixin.android.R
import one.mixin.android.compose.CoilImage
import one.mixin.android.util.SmallTextLoaderComposable
import one.mixin.android.util.TextLoaderComposable
import one.mixin.android.widget.CoilRoundedHexagonTransformation

@Composable
fun TextInscription(iconUrl: String?, contentUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_text_inscirption),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CoilImage(
                model =
                ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .transformations(CoilRoundedHexagonTransformation())
                    .build(),
                modifier = Modifier.size(100.dp),
                placeholder = R.drawable.ic_text_inscription,
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextLoaderComposable(contentUrl, 24.sp)
        }
    }
}

@Composable
fun TextInscription(iconUrl: String?, contentUrl: String?, modifier: Modifier = Modifier, fontSize: TextUnit) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_text_inscirption),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CoilImage(
                model =
                ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .transformations(CoilRoundedHexagonTransformation())
                    .build(),
                modifier = Modifier.size(22.dp),
                placeholder = R.drawable.ic_text_inscription,
            )
            SmallTextLoaderComposable(contentUrl, fontSize)
        }
    }
}
