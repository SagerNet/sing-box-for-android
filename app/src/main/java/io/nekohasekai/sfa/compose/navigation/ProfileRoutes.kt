package io.nekohasekai.sfa.compose.navigation

data class NewProfileArgs(val importName: String? = null, val importUrl: String? = null, val qrsData: ByteArray? = null)

object ProfileRoutes {
    const val NewProfile = "profile/new"
    const val EditProfile = "profile/edit/{profileId}"
    const val EditProfileBase = "profile/edit"

    fun editProfile(profileId: Long): String = "$EditProfileBase/$profileId"
}
