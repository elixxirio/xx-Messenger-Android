package io.xxlabs.messenger.search

import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.xxlabs.messenger.R
import io.xxlabs.messenger.bindings.wrapper.contact.ContactWrapperBase
import io.xxlabs.messenger.data.datatype.FactType
import io.xxlabs.messenger.data.room.model.ContactData
import io.xxlabs.messenger.repository.DaoRepository
import io.xxlabs.messenger.repository.PreferencesRepository
import io.xxlabs.messenger.repository.base.BaseRepository
import io.xxlabs.messenger.requests.data.contact.ContactRequestData
import io.xxlabs.messenger.requests.ui.list.adapter.ContactRequestItem
import io.xxlabs.messenger.requests.ui.list.adapter.EmptyPlaceholderItem
import io.xxlabs.messenger.requests.ui.list.adapter.RequestItem
import io.xxlabs.messenger.support.appContext
import io.xxlabs.messenger.support.toast.ToastUI
import io.xxlabs.messenger.support.util.value
import io.xxlabs.messenger.ui.dialog.info.InfoDialogUI
import io.xxlabs.messenger.ui.dialog.info.TwoButtonInfoDialogUI
import io.xxlabs.messenger.ui.dialog.info.createInfoDialog
import io.xxlabs.messenger.ui.dialog.info.createTwoButtonDialogUi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class UserSearchViewModel @Inject constructor(
    private val repo: BaseRepository,
    private val daoRepo: DaoRepository,
    private val preferences: PreferencesRepository
): ViewModel(){

    private val initialState: SearchUiState by lazy {
        SearchUiState(
            callToActionText = callToActionText,
            placeholderText = placeholderText,
            placeholderVisible = true,
            placeHolderClicked = ::onPlaceholderClicked
        )
    }

    private val userInputState: SearchUiState by lazy {
        SearchUiState()
    }

    private val searchRunningState: SearchUiState by lazy {
        SearchUiState(
            isSearching = true,
            cancelClicked = ::onCancelSearchClicked
        )
    }

    private val searchCompleteState: SearchUiState by lazy {
        SearchUiState(isSearching = false)
    }

    private val callToActionText: Spanned by lazy {
        val highlight = appContext().getColor(R.color.brand_default)
        val cta = appContext().getString(R.string.search_call_to_action)

        val span1 = appContext().getString(R.string.search_call_to_action_span_1)
        val span1Start = cta.indexOf(span1, ignoreCase = true)
        val span1End = span1Start + span1.length

        val span2 = appContext().getString(R.string.search_call_to_action_span_2)
        val span2Start = cta.indexOf(span2, ignoreCase = true)
        val span2End = span2Start + span2.length

        SpannableString(cta).apply {
            setSpan(
                ForegroundColorSpan(highlight),
                span1Start,
                span1End,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(highlight),
                span2Start,
                span2End,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private val placeholderText: Spanned by lazy {
        val highlight = appContext().getColor(R.color.brand_default)
        val text = appContext().getString(R.string.search_placeholder_text)
        val span = appContext().getString(R.string.search_placeholder_span)
        val startIndex = text.indexOf(span, ignoreCase = true)

        SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(highlight),
                startIndex,
                startIndex + span.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    val searchInfoDialog: LiveData<InfoDialogUI?> by ::_searchInfoDialog
    private val _searchInfoDialog = MutableLiveData<InfoDialogUI?>(null)

    private val searchInfoDialogUi: InfoDialogUI by lazy {
        createInfoDialog(
            title = R.string.search_info_dialog_title,
            body = R.string.search_info_dialog_body,
            linkTextToUrlMap = mapOf(
                appContext().getString(R.string.search_info_dialog_link_text)
                        to appContext().getString(R.string.search_info_dialog_link_url)
            )
        )
    }

    val udSearchUi: LiveData<UdSearchUi> by ::_udSearchUi
    private val _udSearchUi = MutableLiveData<UdSearchUi>(initialState)

    val dialogUi: LiveData<TwoButtonInfoDialogUI?> by ::_dialogUi
    private val _dialogUi = MutableLiveData<TwoButtonInfoDialogUI?>(null)

    val toastUi: LiveData<ToastUI?> by ::_toastUi
    private val _toastUi = MutableLiveData<ToastUI?>(null)

    val usernameSearchUi: FactSearchUi by lazy {
        object : FactSearchUi {
            override val countryCode: LiveData<String?> = MutableLiveData(null)
            override val searchHint: String = "Search by username"
            override fun onCountryClicked() {}
            override fun onSearchInput(editable: Editable?) = onUserInput(editable?.toString())
        }
    }
    val emailSearchUi: FactSearchUi by lazy {
        object : FactSearchUi {
            override val countryCode: LiveData<String?> = MutableLiveData(null)
            override val searchHint: String = "Search by email address"
            override fun onCountryClicked() {}
            override fun onSearchInput(editable: Editable?) = onUserInput(editable?.toString())
        }
    }
    val phoneSearchUi: FactSearchUi by lazy {
        object : FactSearchUi {
            override val countryCode: LiveData<String?> = MutableLiveData("🇺🇸 +1")
            override val searchHint: String = "Search by phone number"
            override fun onCountryClicked() {
                onCountryCodeClicked()
            }
            override fun onSearchInput(editable: Editable?) = onUserInput(editable?.toString())
        }
    }

    val usernameResults: Flow<List<RequestItem>> by ::_usernameResults
    private val _usernameResults = MutableStateFlow<List<RequestItem>>(listOf())

    val emailResults: Flow<List<RequestItem>> by ::_emailResults
    private val _emailResults = MutableStateFlow<List<RequestItem>>(listOf())

    val phoneResults: Flow<List<RequestItem>> by ::_phoneResults
    private val _phoneResults = MutableStateFlow<List<RequestItem>>(listOf())

    init {
        showNewUserPopups()
    }

    private fun showNewUserPopups() {
        if (preferences.isFirstTimeNotifications) {
            showNotificationDialog()
        }
    }

    private fun showNotificationDialog() {
        _dialogUi.value = createTwoButtonDialogUi(
            title = R.string.settings_push_notifications_dialog_title,
            body = R.string.settings_push_notifications_dialog_body,
            linkTextToUrlMap = null,
            positiveClick = ::enablePushNotifications,
            negativeClick = null,
            onDismiss = ::showCoverMessageDialog
        )
        preferences.isFirstTimeNotifications = false
    }

    private fun enablePushNotifications() {
        viewModelScope.launch {
            try {
                val notificationToken = enableNotifications()
                onNotificationsEnabled(notificationToken)
            } catch (e: Exception) {
                showToast(
                    e.message ?: "Failed to enable notifications. Please try again in Settings."
                )
            }
        }
    }

    private fun onNotificationsEnabled(token: String) {
        token.run {
            Timber.d("New token successfully sent! $this")
            preferences.areNotificationsOn = true
            preferences.currentNotificationsTokenId = this
            preferences.notificationsTokenId = this
        }
    }

    private suspend fun enableNotifications() = repo.registerNotificationsToken().value()

    private fun showCoverMessageDialog() {
        _dialogUi.value = createTwoButtonDialogUi(
            title = R.string.settings_cover_traffic_title,
            body = R.string.settings_cover_traffic_dialog_body,
            linkTextToUrlMap = mapOf(
                appContext().getString(R.string.settings_cover_traffic_link_text)
                        to appContext().getString(R.string.settings_cover_traffic_link_url)
            ),
            positiveClick = { enableDummyTraffic(true) },
            negativeClick = { enableDummyTraffic(false) },
        )
        preferences.isFirstTimeCoverMessages = false
    }

    private fun enableDummyTraffic(enabled: Boolean) {
        preferences.isCoverTrafficOn = enabled
        repo.enableDummyTraffic(enabled)
    }

    fun onUsernameSearch(username: String?) {
        username?.let {
            val factQuery = FactQuery.UsernameQuery(it)
            search(factQuery, _usernameResults)
        }
    }

    fun onEmailSearch(email: String?) {
        email?.let {
            val factQuery = FactQuery.EmailQuery(it)
            search(factQuery, _emailResults)
        }
    }

    fun onPhoneSearch(phone: String?) {
        phone?.let {
            // TODO: Get cached country code from country picker dialog
            val factQuery = FactQuery.PhoneQuery(it)
            search(factQuery, _phoneResults)
        }
    }

    private fun search(
        factQuery: FactQuery,
        resultsEmitter: MutableStateFlow<List<RequestItem>>
    ) {
        _udSearchUi.value = searchRunningState
        viewModelScope.launch {
            /*  TODO: When the username matches a connections' nickname, search UD too.
                Two users can have the same displayName when
                one username matches the other nickname.
            */
            searchConnections(factQuery).run {
                if (isNotEmpty()) {
                    resultsEmitter.emitResults(this)
                } else {
                    null
                }
            } ?: searchUd(factQuery)?.run {
                resultsEmitter.emitResults(listOf(this))
            } ?: resultsEmitter.emitResults(noResultsFor(factQuery))
        }
    }

    private fun noResultsFor(factQuery: FactQuery): List<RequestItem> =
        listOf(noResultPlaceholder(factQuery))

    private fun noResultPlaceholder(factQuery: FactQuery): RequestItem =
        EmptyPlaceholderItem(
            text = "There are no users with that ${factQuery.type.name.lowercase()}."
        )

    private suspend fun MutableStateFlow<List<RequestItem>>.emitResults(results: List<RequestItem>) {
        emit(results)
        _udSearchUi.value = searchCompleteState
    }

    private suspend fun searchConnections(factQuery: FactQuery): List<RequestItem> {
        return when (factQuery.type) {
            FactType.USERNAME -> {
                daoRepo.connectionsUsernameSearch(factQuery.fact)
                    .value()
                    .asRequestItems()
            }
            FactType.EMAIL -> {
                daoRepo.connectionsEmailSearch(factQuery.fact)
                    .value()
                    .asRequestItems()
            }
            FactType.PHONE -> {
                daoRepo.connectionsPhoneSearch(factQuery.fact)
                    .value()
                    .asRequestItems()
            }
            else -> listOf()
        }
    }

    private fun List<ContactData>.asRequestItems(): List<RequestItem> {
        return mapNotNull {
            ContactRequestItem(ContactRequestData(it))
        }
    }

    private suspend fun searchUd(factQuery: FactQuery): RequestItem? {
        return try {
            val udResult = repo.searchUd(factQuery.fact, factQuery.type).value()
            udResult.second?.let {
                if (it.isNotEmpty()) {
                    // TODO: Display search result error
                    showToast(it)
                    noResultPlaceholder(factQuery)
                } else {
                    udResult.first?.asRequestItem() ?: noResultPlaceholder(factQuery)
                }
            } ?: udResult.first?.asRequestItem() ?: noResultPlaceholder(factQuery)
        } catch (e: Exception) {
            e.message?.let { showToast(it) }
            noResultPlaceholder(factQuery)
        }
    }

    private fun ContactWrapperBase.asRequestItem(): RequestItem {
        // ContactWrapperBase -> ContactRequestData
        val requestData = ContactRequestData(
            ContactData.from(this)
        )
        // ContactRequestData -> RequestItem
        return ContactRequestItem(requestData)
    }

    private fun showToast(error: String) {
        _toastUi.postValue(
            ToastUI.create(
                body = error,
                leftIcon = R.drawable.ic_alert
            )
        )
    }

    fun onToastShown() {
        _toastUi.value = null
    }

    fun onDialogShown() {
        _dialogUi.value = null
    }

    private fun onPlaceholderClicked() {
        _searchInfoDialog.value = searchInfoDialogUi
    }

    fun onInfoDialogShown() {
        _searchInfoDialog.value = null
    }

    private fun onCancelSearchClicked() {
        _udSearchUi.value = searchCompleteState
    }

    private fun onCountryCodeClicked() {
        showToast("Country code")
    }

    fun onUserInput(input: String?) {
        _udSearchUi.value = input?.let {
            userInputState
        } ?: initialState
    }
}

private sealed class FactQuery {
    abstract val fact: String
    abstract val type: FactType

    class UsernameQuery(query: String): FactQuery() {
        override val fact: String = "U$query"
        override val type: FactType = FactType.USERNAME
    }

    class EmailQuery(query: String): FactQuery() {
        override val fact: String = "E$query"
        override val type: FactType = FactType.EMAIL
    }

    class PhoneQuery(query: String): FactQuery() {
        override val fact: String = "P$query"
        override val type: FactType = FactType.PHONE
    }
}

