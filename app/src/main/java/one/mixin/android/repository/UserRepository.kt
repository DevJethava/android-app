package one.mixin.android.repository

import android.os.CancellationSignal
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.Account.PREF_ROUTE_BOT_PK
import one.mixin.android.Constants.RouteConfig.ROUTE_BOT_USER_ID
import one.mixin.android.Constants.RouteConfig.WEB3_BOT_USER_ID
import one.mixin.android.MixinApplication
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.MixinResponseException
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.CircleConversationRequest
import one.mixin.android.api.service.CircleService
import one.mixin.android.api.service.UserService
import one.mixin.android.db.AppDao
import one.mixin.android.db.CircleConversationDao
import one.mixin.android.db.CircleDao
import one.mixin.android.db.ConversationDao
import one.mixin.android.db.MixinDatabase
import one.mixin.android.db.ParticipantSessionDao
import one.mixin.android.db.UserDao
import one.mixin.android.db.provider.DataProvider
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.oneWeekAgo
import one.mixin.android.extension.putString
import one.mixin.android.session.Session
import one.mixin.android.vo.App
import one.mixin.android.vo.Circle
import one.mixin.android.vo.CircleConversation
import one.mixin.android.vo.CircleName
import one.mixin.android.vo.CircleOrder
import one.mixin.android.vo.ConversationCircleManagerItem
import one.mixin.android.vo.ForwardUser
import one.mixin.android.vo.ParticipantSession
import one.mixin.android.vo.SearchBot
import one.mixin.android.vo.User
import one.mixin.android.vo.UserItem
import one.mixin.android.vo.UserRelationship
import one.mixin.android.vo.generateConversationId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository
    @Inject
    constructor(
        private val appDatabase: MixinDatabase,
        private val appDao: AppDao,
        private val circleConversationDao: CircleConversationDao,
        private val circleDao: CircleDao,
        private val circleService: CircleService,
        private val conversationDao: ConversationDao,
        private val participantSessionDao: ParticipantSessionDao,
        private val userDao: UserDao,
        private val userService: UserService,
    ) {
        fun findFriends(): LiveData<List<User>> = userDao.findFriends()

        fun findContacts(): LiveData<List<User>> = userDao.findContacts()

        suspend fun getFriends(): List<User> = userDao.getFriends()

        suspend fun fuzzySearchUser(
            query: String,
            cancellationSignal: CancellationSignal,
        ): List<User> =
            DataProvider.fuzzySearchUser(query, query, query, Session.getAccountId() ?: "", appDatabase, cancellationSignal)

        suspend fun fuzzySearchBots(
            query: String,
            cancellationSignal: CancellationSignal,
        ): List<SearchBot> =
            DataProvider.fuzzySearchBots(query, query, Session.getAccountId() ?: "", appDatabase, cancellationSignal)

        suspend fun searchSuspend(query: String): MixinResponse<User> {
            return userService.searchSuspend(query)
        }

        suspend fun fuzzySearchGroupUser(
            conversationId: String,
            query: String,
        ): List<User> =
            userDao.fuzzySearchGroupUser(conversationId, query, query, Session.getAccountId() ?: "")

        suspend fun fuzzySearchBotGroupUser(
            conversationId: String,
            query: String,
        ): List<User> =
            userDao.fuzzySearchBotGroupUser(conversationId, query, query, Session.getAccountId() ?: "", oneWeekAgo())

        suspend fun suspendGetGroupParticipants(conversationId: String): List<User> =
            userDao.suspendGetGroupParticipants(conversationId, Session.getAccountId() ?: "")

        fun findUserById(query: String): LiveData<User> = userDao.findUserById(query)

        suspend fun suspendFindUserById(query: String) = userDao.suspendFindUserById(query)

        fun getUserById(id: String): User? = userDao.findUser(id)

        fun findForwardUserById(id: String): ForwardUser? = userDao.findForwardUserById(id)

        suspend fun findUserExist(userIds: List<String>): List<String> = userDao.findUserExist(userIds)

        suspend fun findOrRefreshUsers(ids: List<String>): List<User> {
            val users = userDao.suspendFindUserByIds(ids)
            if (users.size == ids.size) return users

            val notExistsUserIds = ids.subtract(users.map { it.userId }.toSet())
            val success =
                handleMixinResponse(
                    invokeNetwork = {
                        userService.getUserByIdsSuspend(notExistsUserIds.toList())
                    },
                    successBlock = {
                        it.data?.let { us ->
                            us.forEach { u ->
                                upsert(u)
                            }
                            return@handleMixinResponse true
                        }
                    },
                ) == true
            return if (success) {
                userDao.suspendFindUserByIds(ids)
            } else {
                emptyList()
            }
        }

        suspend fun refreshUser(id: String): User? {
            val user = userDao.suspendFindUserById(id)
            if (user != null) return user

            return handleMixinResponse(
                invokeNetwork = {
                    userService.getUserByIdSuspend(id)
                },
                successBlock = {
                    it.data?.let { u ->
                        upsert(u)
                        return@handleMixinResponse u
                    }
                },
            )
        }

        suspend fun getAppAndCheckUser(
            id: String,
            updatedAt: String?,
        ): App? {
            val app = findAppById(id)
            if (app?.updatedAt != null && app.updatedAt == updatedAt) {
                return app
            }

            return handleMixinResponse(
                invokeNetwork = {
                    userService.getUserByIdSuspend(id)
                },
                successBlock = {
                    it.data?.let { u ->
                        withContext(Dispatchers.IO) {
                            upsert(u)
                        }
                        return@handleMixinResponse u.app
                    }
                },
            )
        }

        fun findUserByConversationId(conversationId: String): LiveData<User> =
            userDao.findUserByConversationId(conversationId)

        fun findContactByConversationId(conversationId: String): ForwardUser? =
            userDao.findContactByConversationId(conversationId)

        suspend fun suspendFindContactByConversationId(conversationId: String): User? =
            userDao.suspendFindContactByConversationId(conversationId)

        fun findSelf(): LiveData<User?> = userDao.findSelf(Session.getAccountId() ?: "").asLiveData()

        suspend fun upsert(user: User) =
            withContext(Dispatchers.IO) {
                userDao.insertUpdate(user, appDao)
            }

        suspend fun upsertList(users: List<User>) =
            withContext(Dispatchers.IO) {
                userDao.insertUpdateList(users, appDao)
            }

        suspend fun insertApp(app: App) {
            appDao.insertSuspend(app)
        }

        suspend fun upsertBlock(user: User) =
            withContext(Dispatchers.IO) {
                userDao.updateRelationship(user, UserRelationship.BLOCKING.name)
            }

        fun updatePhone(
            id: String,
            phone: String,
        ) = userDao.updatePhone(id, phone)

        suspend fun findAppById(id: String) = appDao.findAppById(id)

        suspend fun searchAppByHost(query: String) = appDao.searchAppByHost("%$query%")

        fun findContactUsers() = userDao.findContactUsers()

        fun allRecipients(): LiveData<List<UserItem>> = userDao.allRecipients()

        suspend fun findFriendsNotBot() = userDao.findFriendsNotBot()

        suspend fun findFriendsAndMyBot() = userDao.findFriendsAndMyBot(Session.getAccountId()!!)

        fun findAppsByIds(appIds: List<String>) = appDao.findAppsByIds(appIds)

        suspend fun findBotsByIds(appIds: Set<String>) = userDao.findMultiUsersByIds(appIds)

        suspend fun getApps() = appDao.getApps()

        suspend fun findMultiUsersByIds(ids: Set<String>) = userDao.findMultiUsersByIds(ids)

        suspend fun findMultiCallUsersByIds(
            conversationId: String,
            ids: Set<String>,
        ) =
            userDao.findMultiCallUsersByIds(conversationId, ids)

        suspend fun findSelfCallUser(
            conversationId: String,
            userId: String,
        ) =
            userDao.findSelfCallUser(conversationId, userId)

        suspend fun fetchUser(ids: List<String>) = userService.fetchUsers(ids)

        suspend fun findUserByIdentityNumberSuspend(identityNumber: String) = userDao.suspendFindUserByIdentityNumber(identityNumber)

        fun insertUser(user: User) = userDao.insertUpdate(user, appDao)

        suspend fun findAppByAppNumber(
            conversationId: String,
            appNumber: String,
        ) = appDao.findAppByAppNumber(conversationId, appNumber)

        suspend fun createCircle(name: String) = circleService.createCircle(CircleName(name))

        fun observeAllCircleItem() = circleDao.observeAllCircleItem()

        suspend fun insertCircle(circle: Circle) = circleDao.insertUpdateSuspend(circle)

        suspend fun circleRename(
            circleId: String,
            name: String,
        ) = circleService.updateCircle(circleId, CircleName(name))

        suspend fun deleteCircle(circleId: String) = circleService.deleteCircle(circleId)

        suspend fun deleteCircleById(circleId: String) = circleDao.deleteCircleById(circleId)

        suspend fun findConversationItemByCircleId(circleId: String) =
            circleDao.findConversationItemByCircleId(circleId)

        suspend fun updateCircleConversations(
            id: String,
            circleConversationRequests: List<CircleConversationRequest>,
        ) =
            circleService.updateCircleConversations(id, circleConversationRequests)

        suspend fun sortCircleConversations(list: List<CircleOrder>?) =
            withContext(Dispatchers.IO) {
                circleDao.updateAll(list)
            }

        suspend fun deleteCircleConversation(
            conversationId: String,
            circleId: String,
        ) =
            circleConversationDao.deleteByIdsSuspend(conversationId, circleId)

        suspend fun insertCircleConversation(circleConversation: CircleConversation) =
            circleConversationDao.insertSuspend(circleConversation)

        suspend fun findCircleConversationByCircleId(circleId: String) =
            circleConversationDao.findCircleConversationByCircleId(circleId)

        suspend fun getIncludeCircleItem(conversationId: String): List<ConversationCircleManagerItem> =
            circleDao.getIncludeCircleItem(conversationId)

        suspend fun getOtherCircleItem(conversationId: String): List<ConversationCircleManagerItem> =
            circleDao.getOtherCircleItem(conversationId)

        fun hasUnreadMessage(circleId: String): LiveData<Boolean> {
            return conversationDao.hasUnreadMessage(circleId).map {
                it != null && it > 0
            }
        }

        suspend fun findCirclesNameByConversationId(conversationId: String) =
            circleDao.findCirclesNameByConversationId(conversationId)

        suspend fun findCircleItemByCircleIdSuspend(circleId: String) =
            circleDao.findCircleItemByCircleIdSuspend(circleId)

        suspend fun getCircleConversationCount(conversationId: String) =
            circleConversationDao.getCircleConversationCount(conversationId)

        suspend fun findUserByAppId(appId: String): User? = userDao.findUserByAppId(appId)

        suspend fun updateMuteUntil(
            id: String,
            muteUntil: String,
        ) = userDao.updateMuteUntilSuspend(id, muteUntil)

        suspend fun fetchSessionsSuspend(ids: List<String>) = userService.fetchSessionsSuspend(ids)

        suspend fun findBotPublicKey(
            conversationId: String,
            botId: String,
        ): String? {
            return participantSessionDao.findBotPublicKey(conversationId, botId)
        }

        suspend fun saveSession(participantSession: ParticipantSession) {
            participantSessionDao.insertSuspend(participantSession)
        }

        fun deleteSessionByUserId(
            conversationId: String,
            userId: String,
        ) {
            participantSessionDao.deleteByUserId(conversationId, userId)
        }

        suspend fun findOrSyncApp(id: String): App? {
            val app = appDao.findAppById(id)
            if (app != null) {
                return app
            }
            return handleMixinResponse(
                invokeNetwork = {
                    userService.getUserByIdSuspend(id)
                },
                successBlock = {
                    it.data?.let { u ->
                        upsert(u)
                        return@handleMixinResponse u.app
                    }
                },
            )
        }

        @Suppress("KotlinConstantConditions")
        suspend fun getBotPublicKey(botId: String, force: Boolean) {
            if (botId != ROUTE_BOT_USER_ID) return

            val key =
                findBotPublicKey(
                    generateConversationId(
                        botId,
                        Session.getAccountId()!!,
                    ),
                    botId,
                )
            if (key != null && !force) {
                if (botId == ROUTE_BOT_USER_ID) {
                    MixinApplication.appContext.defaultSharedPreferences.putString(PREF_ROUTE_BOT_PK, key)
                }
            } else {
                val sessionResponse = fetchSessionsSuspend(listOf(botId))
                if (sessionResponse.isSuccess) {
                    val sessionData = requireNotNull(sessionResponse.data)[0]
                    saveSession(
                        ParticipantSession(
                            generateConversationId(
                                sessionData.userId,
                                Session.getAccountId()!!,
                            ),
                            sessionData.userId,
                            sessionData.sessionId,
                            publicKey = sessionData.publicKey,
                        ),
                    )
                    if (botId == ROUTE_BOT_USER_ID) {
                        MixinApplication.appContext.defaultSharedPreferences.putString(PREF_ROUTE_BOT_PK, sessionData.publicKey)
                    }
                } else {
                    throw MixinResponseException(
                        sessionResponse.errorCode,
                        sessionResponse.errorDescription,
                    )
                }
            }
        }
    }
