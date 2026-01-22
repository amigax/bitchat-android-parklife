package com.bitchat.android.ui

import com.bitchat.android.R
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/**
 * Handles processing of IRC-style commands
 */
class CommandProcessor(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val privateChatManager: PrivateChatManager
) {

    // Available commands list
    private val baseCommands = listOf(
        CommandSuggestion("/block", emptyList(), "[nickname]", "block or list blocked peers"),
        CommandSuggestion("/channels", emptyList(), null, "show all discovered channels"),
        CommandSuggestion("/clear", emptyList(), null, "clear chat messages"),
        CommandSuggestion("/figlet", emptyList(), "<text>", "generate ASCII art text"),
        CommandSuggestion("/hug", emptyList(), "<nickname>", "send someone a warm hug"),
        CommandSuggestion("/insult", emptyList(), "<nickname>", "generate a random insult"),
        CommandSuggestion("/j", listOf("/join"), "<channel>", "join or create a channel"),
        CommandSuggestion("/m", listOf("/msg"), "<nickname> [message]", "send private message"),
        CommandSuggestion("/say", emptyList(), "<text>", "speak the text out loud"),
        CommandSuggestion("/sayall", emptyList(), null, "toggle speaking all incoming messages"),
        CommandSuggestion("/slap", emptyList(), "<nickname>", "slap someone with a trout"),
        CommandSuggestion("/unblock", emptyList(), "<nickname>", "unblock a peer"),
        CommandSuggestion("/w", emptyList(), null, "see who's online"),
        //GAZ
        CommandSuggestion("/sapme", emptyList(), null, "request a frosty beer for yourself!"),
        CommandSuggestion("/saphim", emptyList(), "<nickname>", "request a frosty beer for someone else!"),
        CommandSuggestion("/saysapme", emptyList(), null, "say and play the sap me sound"),
        //GAZ
    )

    // MARK: - Command Processing

    fun processCommand(command: String, meshService: BluetoothMeshService, myPeerID: String, onSendMessage: (String, List<String>, String?) -> Unit, viewModel: ChatViewModel? = null): Boolean {
        if (!command.startsWith("/")) return false

        val parts = command.split(" ")
        val cmd = parts.first().lowercase()
        when (cmd) {
            "/j", "/join" -> handleJoinCommand(parts, myPeerID)
            "/m", "/msg" -> handleMessageCommand(parts, meshService, onSendMessage)
            "/w" -> handleWhoCommand(meshService, viewModel)
            "/clear" -> handleClearCommand()
            "/pass" -> handlePassCommand(parts, myPeerID)
            "/block" -> handleBlockCommand(parts, meshService)
            "/unblock" -> handleUnblockCommand(parts, meshService)
            "/say" -> handleSayCommand(parts, viewModel, myPeerID)
            "/sayall" -> handleSayAllCommand(viewModel)
            "/hug" -> handleActionCommand(parts, "gives", "a warm hug 🫂", meshService, myPeerID, onSendMessage, viewModel)
            "/slap" -> handleActionCommand(parts, "slaps", "around a bit with a large trout 🐟", meshService, myPeerID, onSendMessage, viewModel)
            "/insult" -> handleInsultCommand(parts, myPeerID, onSendMessage)
            "/figlet" -> handleFigletCommand(parts, viewModel)
            "/sapme" -> handleSelfActionCommand("requests a frosty beer! Sapporo me captain! 🍺🍺🍺🍺🍺", meshService, myPeerID, onSendMessage, viewModel)
            "/saphim" -> handleActionCommand(parts, "requests a frosty beer for", "! 🍺 Sapporo him captain!! 🍺🍺🍺🍺🍺", meshService, myPeerID, onSendMessage, viewModel)
            "/saysapme" -> handleSayAndPlayCommand("Sapporo me captain! 🍺", R.raw.sapporome, viewModel, myPeerID)
            "/channels" -> handleChannelsCommand()
            else -> handleUnknownCommand(cmd)
        }

        return true
    }

    private fun handleFigletCommand(parts: List<String>, viewModel: ChatViewModel?) {
        if (parts.size > 1) {
            val textToFiglet = parts.drop(1).joinToString(" ")
            viewModel?.sendFigletMessage(textToFiglet)
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /figlet <text>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleSayAllCommand(viewModel: ChatViewModel?) {
        viewModel?.toggleSayAll()
        val enabled = viewModel?.sayAllEnabled?.value ?: false
        val status = if (enabled) "enabled" else "disabled"
        val systemMessage = BitchatMessage(
            sender = "system",
            content = "say all messages is now $status",
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleSayAndPlayCommand(text: String, soundResId: Int, viewModel: ChatViewModel?, myPeerID: String) {
        val message = BitchatMessage(
            sender = state.getNicknameValue() ?: myPeerID,
            content = text,
            timestamp = Date(),
            isRelay = false,
            senderPeerID = myPeerID,
            channel = state.getCurrentChannelValue()
        )
        if (state.getCurrentChannelValue() != null) {
            channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
        } else {
            messageManager.addMessage(message)
        }

        viewModel?.speak(text)
        viewModel?.viewModelScope?.launch {
            delay(2000)
            viewModel.playSound(soundResId)
        }
    }

    private fun handleSayCommand(parts: List<String>, viewModel: ChatViewModel?, myPeerID: String) {
        if (parts.size > 1) {
            val textToSpeak = parts.drop(1).joinToString(" ")

            val message = BitchatMessage(
                sender = state.getNicknameValue() ?: myPeerID,
                content = textToSpeak,
                timestamp = Date(),
                isRelay = false,
                senderPeerID = myPeerID,
                channel = state.getCurrentChannelValue()
            )
            if (state.getCurrentChannelValue() != null) {
                channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
            } else {
                messageManager.addMessage(message)
            }

            viewModel?.speak(textToSpeak)
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /say <text>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleJoinCommand(parts: List<String>, myPeerID: String) {
        if (parts.size > 1) {
            val channelName = parts[1]
            val channel = if (channelName.startsWith("#")) channelName else "#$channelName"
            val password = if (parts.size > 2) parts[2] else null
            val success = channelManager.joinChannel(channel, password, myPeerID)
            if (success) {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "joined channel $channel",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /join <channel>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleMessageCommand(parts: List<String>, meshService: BluetoothMeshService, onSendMessage: (String, List<String>, String?) -> Unit) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")

            if (targetName.equals("PopManBot", ignoreCase = true)) {
                if (parts.size > 2) {
                    val botCommand = parts.drop(2).joinToString(" ")
                    
                    // Create the local echo message from the bot
                    val botMessage = BitchatMessage(
                        sender = "PopManBot",
                        content = botCommand,
                        timestamp = Date(),
                        isRelay = false
                    )
                    messageManager.addMessage(botMessage)

                    // Send the secret command to other clients
                    val secretMessage = "BOT_MSG::$botCommand"
                    onSendMessage(secretMessage, emptyList(), state.getCurrentChannelValue())
                } else {
                     val systemMessage = BitchatMessage(
                        sender = "system",
                        content = "usage: /m PopManBot <command>",
                        timestamp = Date(),
                        isRelay = false
                    )
                    messageManager.addMessage(systemMessage)
                }
                return
            }


            val peerID = getPeerIDForNickname(targetName, meshService)

            if (peerID != null) {
                val success = privateChatManager.startPrivateChat(peerID, meshService)

                if (success) {
                    if (parts.size > 2) {
                        val messageContent = parts.drop(2).joinToString(" ")
                        val recipientNickname = getPeerNickname(peerID, meshService)
                        privateChatManager.sendPrivateMessage(
                            messageContent,
                            peerID,
                            recipientNickname,
                            state.getNicknameValue(),
                            getMyPeerID(meshService)
                        ) { content, peerIdParam, recipientNicknameParam, messageId ->
                            // This would trigger the actual mesh service send
                            sendPrivateMessageVia(meshService, content, peerIdParam, recipientNicknameParam, messageId)
                        }
                    } else {
                        val systemMessage = BitchatMessage(
                            sender = "system",
                            content = "started private chat with $targetName",
                            timestamp = Date(),
                            isRelay = false
                        )
                        messageManager.addMessage(systemMessage)
                    }
                }
            } else {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "user '$targetName' not found. they may be offline or using a different nickname.",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /msg <nickname> [message]",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleWhoCommand(meshService: BluetoothMeshService, viewModel: ChatViewModel? = null) {
        // Channel-aware who command (matches iOS behavior)
        val (peerList, contextDescription) = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.bitchat.android.geohash.ChannelID.Mesh,
                null -> {
                    // Mesh channel: show Bluetooth-connected peers
                    val connectedPeers = state.getConnectedPeersValue()
                    val peerNicknames = connectedPeers.map { peerID -> getPeerNickname(peerID, meshService) }.toMutableList()
                    peerNicknames.add(0, "👑PopManBot")
                    val peerList = peerNicknames.joinToString(", ")
                    Pair(peerList, "online users")
                }

                is com.bitchat.android.geohash.ChannelID.Location -> {
                    // Location channel: show geohash participants
                    val geohashPeople = viewModel.geohashPeople.value ?: emptyList()
                    val currentNickname = state.getNicknameValue()

                    val participantList = geohashPeople.mapNotNull { person ->
                        val displayName = person.displayName
                        // Exclude self from list
                        if (displayName.startsWith("${currentNickname}#")) {
                            null
                        } else {
                            displayName
                        }
                    }.toMutableList()
                    participantList.add(0, "👑PopManBot")
                    val peerList = participantList.joinToString(", ")

                    Pair(peerList, "participants in ${selectedChannel.channel.geohash}")
                }
            }
        } else {
            // Fallback to mesh behavior
            val connectedPeers = state.getConnectedPeersValue()
            val peerNicknames = connectedPeers.map { peerID -> getPeerNickname(peerID, meshService) }.toMutableList()
            peerNicknames.add(0, "👑PopManBot")
            val peerList = peerNicknames.joinToString(", ")
            Pair(peerList, "online users")
        }

        val systemMessage = BitchatMessage(
            sender = "system",
            content = if (peerList.isEmpty()) {
                "Just you and PopManBot are here."
            } else {
                "$contextDescription: $peerList"
            },
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleClearCommand() {
        when {
            state.getSelectedPrivateChatPeerValue() != null -> {
                // Clear private chat
                val peerID = state.getSelectedPrivateChatPeerValue()!!
                messageManager.clearPrivateMessages(peerID)
            }
            state.getCurrentChannelValue() != null -> {
                // Clear channel messages
                val channel = state.getCurrentChannelValue()!!
                messageManager.clearChannelMessages(channel)
            }
            else -> {
                // Clear main messages
                messageManager.clearMessages()
            }
        }
    }

    private fun handlePassCommand(parts: List<String>, peerID: String) {
        val currentChannel = state.getCurrentChannelValue()

        if (currentChannel == null) {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "you must be in a channel to set a password.",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return
        }

        if (parts.size == 2){
            if(!channelManager.isChannelCreator(channel = currentChannel, peerID = peerID)){
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "you must be the channel creator to set a password.",
                    timestamp = Date(),
                    isRelay = false
                )
                channelManager.addChannelMessage(currentChannel,systemMessage,null)
                return
            }
            val newPassword = parts[1]
            channelManager.setChannelPassword(currentChannel, newPassword)
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "password changed for channel $currentChannel",
                timestamp = Date(),
                isRelay = false
            )
            channelManager.addChannelMessage(currentChannel,systemMessage,null)
        }
        else{
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /pass <password>",
                timestamp = Date(),
                isRelay = false
            )
            channelManager.addChannelMessage(currentChannel,systemMessage,null)
        }
    }

    private fun handleBlockCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            privateChatManager.blockPeerByNickname(targetName, meshService)
        } else {
            // List blocked users
            val blockedInfo = privateChatManager.listBlockedUsers()
            val systemMessage = BitchatMessage(
                sender = "system",
                content = blockedInfo,
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleUnblockCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            privateChatManager.unblockPeerByNickname(targetName, meshService)
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /unblock <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleActionCommand(
        parts: List<String>,
        verb: String,
        object_: String,
        meshService: BluetoothMeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit,
        viewModel: ChatViewModel? = null
    ) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val actionMessage = "* ${state.getNicknameValue() ?: "someone"} $verb $targetName $object_ *"

            // If we're in a geohash location channel, don't add a local echo here.
            // GeohashViewModel.sendGeohashMessage() will add the local echo with proper metadata.
            val isInLocationChannel = state.selectedLocationChannel.value is com.bitchat.android.geohash.ChannelID.Location

            viewModel?.playSound(R.raw.sapporome)

            // Send as regular message
            if (state.getSelectedPrivateChatPeerValue() != null) {
                val peerID = state.getSelectedPrivateChatPeerValue()!!
                privateChatManager.sendPrivateMessage(
                    actionMessage,
                    peerID,
                    getPeerNickname(peerID, meshService),
                    state.getNicknameValue(),
                    myPeerID
                ) { content, peerIdParam, recipientNicknameParam, messageId ->
                    sendPrivateMessageVia(meshService, content, peerIdParam, recipientNicknameParam, messageId)
                }
            } else if (isInLocationChannel) {
                // Let the transport layer add the echo; just send it out
                onSendMessage(actionMessage, emptyList(), null)
            } else {
                val message = BitchatMessage(
                    sender = state.getNicknameValue() ?: myPeerID,
                    content = actionMessage,
                    timestamp = Date(),
                    isRelay = false,
                    senderPeerID = myPeerID,
                    channel = state.getCurrentChannelValue()
                )

                if (state.getCurrentChannelValue() != null) {
                    channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
                    onSendMessage(actionMessage, emptyList(), state.getCurrentChannelValue())
                } else {
                    messageManager.addMessage(message)
                    onSendMessage(actionMessage, emptyList(), null)
                }
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /${parts[0].removePrefix("/")} <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleSelfActionCommand(
        actionText: String,
        meshService: BluetoothMeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit,
        viewModel: ChatViewModel? = null
    ) {
        val senderNickname = state.getNicknameValue() ?: "someone"
        val actionMessage = "* $senderNickname $actionText *"

        val isInLocationChannel = state.selectedLocationChannel.value is com.bitchat.android.geohash.ChannelID.Location

        viewModel?.playSound(R.raw.sapporome)

        if (state.getSelectedPrivateChatPeerValue() != null) {
            val peerID = state.getSelectedPrivateChatPeerValue()!!
            privateChatManager.sendPrivateMessage(
                actionMessage,
                peerID,
                getPeerNickname(peerID, meshService),
                state.getNicknameValue(),
                myPeerID
            ) { content, peerIdParam, recipientNicknameParam, messageId ->
                sendPrivateMessageVia(meshService, content, peerIdParam, recipientNicknameParam, messageId)
            }
        } else if (isInLocationChannel) {
            onSendMessage(actionMessage, emptyList(), null)
        } else {
            val message = BitchatMessage(
                sender = state.getNicknameValue() ?: myPeerID,
                content = actionMessage,
                timestamp = Date(),
                isRelay = false,
                senderPeerID = myPeerID,
                channel = state.getCurrentChannelValue()
            )

            if (state.getCurrentChannelValue() != null) {
                channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
                onSendMessage(actionMessage, emptyList(), state.getCurrentChannelValue())
            } else {
                messageManager.addMessage(message)
                onSendMessage(actionMessage, emptyList(), null)
            }
        }
    }

    private fun handleChannelsCommand() {
        val allChannels = channelManager.getJoinedChannelsList()
        val channelList = if (allChannels.isEmpty()) {
            "no channels joined"
        } else {
            "joined channels: ${allChannels.joinToString(", ")}"
        }

        val systemMessage = BitchatMessage(
            sender = "system",
            content = channelList,
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleUnknownCommand(cmd: String) {
        val systemMessage = BitchatMessage(
            sender = "system",
            content = "unknown command: $cmd. type / to see available commands.",
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    // MARK: - Command Autocomplete

    fun updateCommandSuggestions(input: String) {
        if (!input.startsWith("/")) {
            state.setShowCommandSuggestions(false)
            state.setCommandSuggestions(emptyList())
            return
        }

        // Get all available commands based on context
        val allCommands = getAllAvailableCommands()

        // Filter commands based on input
        val filteredCommands = filterCommands(allCommands, input.lowercase())

        if (filteredCommands.isNotEmpty()) {
            state.setCommandSuggestions(filteredCommands)
            state.setShowCommandSuggestions(true)
        } else {
            state.setShowCommandSuggestions(false)
            state.setCommandSuggestions(emptyList())
        }
    }

    private fun getAllAvailableCommands(): List<CommandSuggestion> {
        // Add channel-specific commands if in a channel
        val channelCommands = if (state.getCurrentChannelValue() != null) {
            listOf(
                CommandSuggestion("/pass", emptyList(), "[password]", "change channel password"),
                CommandSuggestion("/save", emptyList(), null, "save channel messages locally"),
                CommandSuggestion("/transfer", emptyList(), "<nickname>", "transfer channel ownership")
            )
        } else {
            emptyList()
        }

        return baseCommands + channelCommands
    }

    private fun filterCommands(commands: List<CommandSuggestion>, input: String): List<CommandSuggestion> {
        return commands.filter { command ->
            // Check primary command
            command.command.startsWith(input) ||
            // Check aliases
            command.aliases.any { it.startsWith(input) }
        }.sortedBy { it.command }
    }

    fun selectCommandSuggestion(suggestion: CommandSuggestion): String {
        state.setShowCommandSuggestions(false)
        state.setCommandSuggestions(emptyList())
        return "${suggestion.command} "
    }

    // MARK: - Mention Autocomplete

    fun updateMentionSuggestions(input: String, meshService: BluetoothMeshService, viewModel: ChatViewModel? = null) {
        // Check if input contains @ and we're at the end of a word or at the end of input
        val atIndex = input.lastIndexOf('@')
        if (atIndex == -1) {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
            return
        }

        // Get the text after the @ symbol
        val textAfterAt = input.substring(atIndex + 1)

        // If there's a space after @, don't show suggestions
        if (textAfterAt.contains(' ')) {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
            return
        }

        // Get peer candidates based on active channel (matches iOS logic exactly)
        val peerCandidates: List<String> = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.bitchat.android.geohash.ChannelID.Mesh,
                null -> {
                    // Mesh channel: use Bluetooth mesh peer nicknames
                    meshService.getPeerNicknames().values.filter { it != meshService.getPeerNicknames()[meshService.myPeerID] }
                }

                is com.bitchat.android.geohash.ChannelID.Location -> {
                    // Location channel: use geohash participants with collision-resistant suffixes
                    val geohashPeople = viewModel.geohashPeople.value ?: emptyList()
                    val currentNickname = state.getNicknameValue()

                    geohashPeople.mapNotNull { person ->
                        val displayName = person.displayName
                        // Exclude self from suggestions
                        if (displayName.startsWith("${currentNickname}#")) {
                            null
                        } else {
                            displayName
                        }
                    }
                }
            }
        } else {
            // Fallback to mesh peers if no viewModel available
            meshService.getPeerNicknames().values.filter { it != meshService.getPeerNicknames()[meshService.myPeerID] }
        }

        // Filter nicknames based on the text after @
        val filteredNicknames = peerCandidates.filter { nickname ->
            nickname.startsWith(textAfterAt, ignoreCase = true)
        }.sorted()

        if (filteredNicknames.isNotEmpty()) {
            state.setMentionSuggestions(filteredNicknames)
            state.setShowMentionSuggestions(true)
        } else {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
        }
    }

    fun selectMentionSuggestion(nickname: String, currentText: String): String {
        state.setShowMentionSuggestions(false)
        state.setMentionSuggestions(emptyList())

        // Find the last @ symbol position
        val atIndex = currentText.lastIndexOf('@')
        if (atIndex == -1) {
            return "$currentText@$nickname "
        }

        // Replace the text from the @ symbol to the end with the mention
        val textBeforeAt = currentText.substring(0, atIndex)
        return "$textBeforeAt@$nickname "
    }

    // MARK: - Insult Command
    private val insultLines = listOf(
        "%u, you have the most %a %d, and %f %b I have ever seen!",
        "%u loves %c %d.",
        "%u likes to suck on %g!",
        "I think %u has %f %b and %f %d.",
        "%u you are %h.",
        "I saw %u %c %g!!",
        "%u looks forward to %c %g!",
        "%u, you have no %b and I saw you %c %h",
        "%u you are the %a person who ever walked the Earth!",
        "%u prepare for your meal....it is a side salad topped with %d",
        "%u, Im going to beat you to death with %h because you love %c %g!",
        "%u you are a rotton lump of turd floating around sucking on %d",
        "%u, didnt I see you last night %c %f %g ?",
        "%u you are %h and your mother is %h who loves %c %d!",
        "%u you wear the %a hats which have -> I have no %b <- on them.",
        "%u. Wheres your %b gone? Without it your just a bag of %d!"
    )
    private val insultA = listOf("smeggiest", "rubbishest", "smelliest", "cackest", "shitest", "narrowest", "fattest")
    private val insultB = listOf("style", "curly hair", "dress sense", "teeth", "mother", "left ventricle", "sophistication")
    private val insultC = listOf("chompping on", "revising with", "collecting", "looking at holiday snapshots of", "socialising with", "wiping peoples bottoms with", "packing peoples pants with ample amounts of")
    private val insultD = listOf("Rimmers Underpants", "lead models of Rimmers mum", "Listers old turds", "Krytens spare heads", "Listers used hankerchiefs", "mouldy lumps of dog turd", "shaven headed bald people")
    private val insultF = listOf("the Most Rimmer like", "the smeggiest", "the greasiest", "the gittiest", "the most stark raving mad", "the worst", "the most comical")
    private val insultG = listOf("toes", "socks", "urine samples", "urine filled caskets", "424 CPU iDENT chips", "weebles", "brown leather satchels", "photographs of listers toes", "blow football sets", "small sacks of canoeing gear", "dogs", "cans of Wicked strength lager")
    private val insultH = listOf("a half eaten lolly pop head", "a smeg head", "a genetically deformed lumpfish", "a wickstand head", "a meat tenderiser head", "one of herman munsters stunt doubles", "a piece of sputom in the toilet of life", "a weasly scum sucking liar", "a goit", "a git with all the charm and wit of a public louse", "a old series 3000 without a slideback sunroof head", "a smeg head who could possibly be drink tommorows lunch through a straw", "a pollop on the anus of humanity", "a floating turd in the rock pool of life", "a sack of listers old socks", "a urine filled casket", "a weeble", "a disgusting dung heap")

    private fun handleInsultCommand(parts: List<String>, myPeerID: String, onSendMessage: (String, List<String>, String?) -> Unit) {
        if (parts.size < 2) {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /insult <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return
        }

        val targetName = parts[1].removePrefix("@")
        var insult = insultLines.random()

        insult = insult.replace("%u", targetName)
        while (insult.contains("%a")) insult = insult.replaceFirst("%a", insultA.random())
        while (insult.contains("%b")) insult = insult.replaceFirst("%b", insultB.random())
        while (insult.contains("%c")) insult = insult.replaceFirst("%c", insultC.random())
        while (insult.contains("%d")) insult = insult.replaceFirst("%d", insultD.random())
        while (insult.contains("%f")) insult = insult.replaceFirst("%f", insultF.random())
        while (insult.contains("%g")) insult = insult.replaceFirst("%g", insultG.random())
        while (insult.contains("%h")) insult = insult.replaceFirst("%h", insultH.random())

        val message = BitchatMessage(
            sender = state.getNicknameValue() ?: myPeerID,
            content = insult,
            timestamp = Date(),
            isRelay = false,
            senderPeerID = myPeerID,
            channel = state.getCurrentChannelValue()
        )

        if (state.getCurrentChannelValue() != null) {
            channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
            onSendMessage(insult, emptyList(), state.getCurrentChannelValue())
        } else {
            messageManager.addMessage(message)
            onSendMessage(insult, emptyList(), null)
        }
    }


    // MARK: - Utility Functions

    private fun getPeerIDForNickname(nickname: String, meshService: BluetoothMeshService): String? {
        return meshService.getPeerNicknames().entries.find { it.value == nickname }?.key
    }

    private fun getPeerNickname(peerID: String, meshService: BluetoothMeshService): String {
        return meshService.getPeerNicknames()[peerID] ?: peerID
    }

    private fun getMyPeerID(meshService: BluetoothMeshService): String {
        return meshService.myPeerID
    }

    private fun sendPrivateMessageVia(meshService: BluetoothMeshService, content: String, peerID: String, recipientNickname: String, messageId: String) {
        meshService.sendPrivateMessage(content, peerID, recipientNickname, messageId)
    }
}
