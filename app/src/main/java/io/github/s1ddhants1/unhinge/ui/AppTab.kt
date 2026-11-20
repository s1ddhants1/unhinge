package io.github.s1ddhants1.unhinge.ui

import androidx.annotation.StringRes
import io.github.s1ddhants1.unhinge.R

enum class AppTab(@StringRes val titleRes: Int) {
    Candidates(R.string.nav_tab_title_candidates),
    Matches(R.string.nav_tab_title_matches),
    Home(R.string.nav_tab_title_home),
    Profile(R.string.nav_tab_title_profile)
}
