package me.rerere.rikkahub.ui.pages.knowledge

sealed interface ImportType {
    data object File : ImportType
    data object ChatHistory : ImportType
    data object TextNote : ImportType
    data object BatchFolder : ImportType
}
