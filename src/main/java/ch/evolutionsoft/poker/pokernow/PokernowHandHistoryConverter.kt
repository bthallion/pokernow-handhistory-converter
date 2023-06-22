package ch.evolutionsoft.poker.pokernow

import ch.evolutionsoft.poker.pokernow.ConversionUtils.readIdPrefixFromDatetime
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readConvertOmahaHighHands
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readConvertOmahaHighLowHands
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readConvertTexasHands
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readCurrencyFactor
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readDestinationFolderProperty
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readNamesProperties
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readSourceFolderProperty
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.LogManager
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.*

class PokerNowHandHistoryConverter {
    private var handIdPrefix: Long = 20200000
    private var convertOmahaHighHands = true
    private var convertOmahaHighLowHands = true
    private var convertTexasHands = false
    var currencyFactor = 10.0
    private var nameMappingsProperties: Properties? = null
    var currentConversionFileName = StringUtils.EMPTY
    private var conversionErrors = StringUtils.EMPTY
    private var singleHandConverter: PokerNowSingleHandConverter? = null
    private fun readConversionParameters() {
        try {
            currencyFactor = readCurrencyFactor()
            convertOmahaHighHands = readConvertOmahaHighHands()
            convertOmahaHighLowHands = readConvertOmahaHighLowHands()
            convertTexasHands = readConvertTexasHands()
            singleHandConverter = PokerNowSingleHandConverter()
            converterLog.info("Using currencyFactor {}", currencyFactor)
            converterLog.info("Using convertOmahaHighHands {}", convertOmahaHighHands)
            converterLog.info("Using convertOmahaHiLoHands {}", convertOmahaHighLowHands)
            converterLog.info("Using convertTexasHands {}", convertTexasHands)
        } catch (e: FileNotFoundException) {
            converterLog.error(PROPERTIES_NOT_FOUND_MESSAGE)
        } catch (e: IOException) {
            converterLog.error(IO_EXCEPTION_MESSAGE)
        }
    }

    fun getSourceFiles(files: Array<File>?): List<File> {
        val sourceFiles: MutableList<File> = LinkedList()
        if (files != null) {
            for (currentFile in files) {
                val currentFileName = currentFile.name
                if (currentFileName.endsWith(".csv")) {
                    sourceFiles.add(currentFile)
                }
            }
        }
        return sourceFiles
    }

    @Throws(IOException::class)
    fun replaceBetAmounts(convertedHandHistory: String?): String? {
        val bufferedReader = BufferedReader(StringReader(convertedHandHistory.toString()))
        var currentLine = bufferedReader.readLine()
        var betFactorConvertedHandHistory: String? = StringUtils.EMPTY
        while (null != currentLine) {
            if (currentLine.contains(ConversionConstants.DOLLAR_SIGN) && !currentLine.contains(PokerStarsConstants.POKER_STARS_HAND)) {
                betFactorConvertedHandHistory =
                    handleBetOrRaiseCurrencyAmount(currentLine, betFactorConvertedHandHistory)
            } else if (currentLine.contains(PokerStarsConstants.POKER_STARS_HAND)) {
                betFactorConvertedHandHistory = replacePokerStarsHandHeaderCurrencyAmounts(
                    currentLine,
                    betFactorConvertedHandHistory
                )
            } else {
                betFactorConvertedHandHistory += currentLine + System.lineSeparator()
            }
            currentLine = bufferedReader.readLine()
        }
        return betFactorConvertedHandHistory
    }

    private fun handleBetOrRaiseCurrencyAmount(currentLine: String, betFactorConvertedHandHistory: String?): String? {
        var tempBetFactorConvertedHandHistory = betFactorConvertedHandHistory
        val indexOfNextSpace =
            currentLine.indexOf(StringUtils.SPACE, currentLine.indexOf(PokerStarsConstants.DOLLAR_CHAR))
        val indexOfNextParenthesis = currentLine.indexOf(')', currentLine.indexOf(PokerStarsConstants.DOLLAR_CHAR))
        if (currentLine.contains(PokerStarsConstants.RAISES_ACTION)) {
            val firstAmount =
                currentLine.substring(currentLine.indexOf(PokerStarsConstants.DOLLAR_CHAR) + 1, indexOfNextSpace)
            val secondAmount =
                currentLine.substring(currentLine.lastIndexOf(PokerStarsConstants.DOLLAR_CHAR) + 1, currentLine.length)
            val convertedAmount1 = firstAmount.toDouble() / currencyFactor
            val convertedAmount2 = secondAmount.toDouble() / currencyFactor
            var betFactorConvertedLine: String = currentLine.replaceFirst(
                ConversionConstants.CURRENCY_AMOUNT_REGEX.toRegex(),
                ConversionConstants.ESCAPED_CURRENCY + convertedAmount1
            )
            betFactorConvertedLine = betFactorConvertedLine.replace(
                PokerStarsConstants.DOLLAR_CHAR.toString() + secondAmount,
                PokerStarsConstants.DOLLAR_CHAR.toString() + String.format(
                    ConversionConstants.AMOUNT_DOUBLE_FORMAT,
                    convertedAmount2
                )
            )
            tempBetFactorConvertedHandHistory += betFactorConvertedLine + System.lineSeparator()
        } else {
            val amount: String = if (indexOfNextSpace >= 0 && !currentLine.contains("Uncalled")) {
                currentLine.substring(currentLine.indexOf(PokerStarsConstants.DOLLAR_CHAR) + 1, indexOfNextSpace)
            } else if (indexOfNextParenthesis >= 0) {
                currentLine.substring(currentLine.indexOf(PokerStarsConstants.DOLLAR_CHAR) + 1, indexOfNextParenthesis)
            } else {
                currentLine.substring(currentLine.indexOf(PokerStarsConstants.DOLLAR_CHAR) + 1, currentLine.length)
            }
            val convertedAmount = amount.toDouble() / currencyFactor
            val betFactorConvertedLine: String = currentLine.replaceFirst(
                ConversionConstants.CURRENCY_AMOUNT_REGEX.toRegex(),
                ConversionConstants.ESCAPED_CURRENCY + String.format(
                    ConversionConstants.AMOUNT_DOUBLE_FORMAT,
                    convertedAmount
                )
            )
            tempBetFactorConvertedHandHistory += betFactorConvertedLine + System.lineSeparator()
        }
        return tempBetFactorConvertedHandHistory
    }

    private fun replacePokerStarsHandHeaderCurrencyAmounts(
        currentLine: String,
        betFactorConvertedHandHistory: String?
    ): String? {
        var tempBetFactorConvertedHandHistory = betFactorConvertedHandHistory
        val originalSmallBlindAmount = currentLine.substring(
            currentLine.indexOf("($") + 2,
            currentLine.indexOf(ConversionConstants.FORWARD_SLASH + ConversionConstants.DOLLAR_SIGN)
        )
        val originalBigBlindAmount = currentLine.substring(
            currentLine.indexOf(PokerStarsConstants.SLASH_DOLLAR) + 2,
            currentLine.indexOf(StringUtils.SPACE, currentLine.indexOf(PokerStarsConstants.SLASH_DOLLAR))
        )
        val convertedSmallBlindAmount = originalSmallBlindAmount.toDouble() / currencyFactor
        val convertedBigBlindAmount = originalBigBlindAmount.toDouble() / currencyFactor
        var handHeaderBlindsConvertedLine = currentLine.replaceFirst(
            "\\($ConversionConstants.CURRENCY_AMOUNT_REGEX".toRegex(),
            "(" + ConversionConstants.ESCAPED_CURRENCY + String.format(
                ConversionConstants.AMOUNT_DOUBLE_FORMAT,
                convertedSmallBlindAmount
            )
        )
        handHeaderBlindsConvertedLine = handHeaderBlindsConvertedLine.replaceFirst(
            (
                    ConversionConstants.FORWARD_SLASH + ConversionConstants.CURRENCY_AMOUNT_REGEX).toRegex(),
            ConversionConstants.FORWARD_SLASH + ConversionConstants.ESCAPED_CURRENCY + String.format(
                ConversionConstants.AMOUNT_DOUBLE_FORMAT,
                convertedBigBlindAmount
            )
        )
        tempBetFactorConvertedHandHistory += handHeaderBlindsConvertedLine + System.lineSeparator()
        return tempBetFactorConvertedHandHistory
    }

    @Throws(IOException::class)
    fun convertHandHistory(handHistory: String, currentFileName: String?): String? {
        conversionErrors = StringUtils.EMPTY
        val indexOfHandEnd: Int = handHistory.indexOf(PokerNowConstants.STARTING_HAND)
        if (indexOfHandEnd < 0) {
            converterLog.info("No hands to convert found yet in {}", currentFileName)
            return StringUtils.EMPTY
        }
        val strippedHandHistory = handHistory.substring(indexOfHandEnd)
        val initialSummaryInfo = strippedHandHistory.substring(0, strippedHandHistory.indexOf(StringUtils.LF))
        var convertedHandHistoryBase =
            initialSummaryInfo + StringUtils.LF + handHistory.substring(handHistory.indexOf(PokerNowConstants.INTRO_TEXT_END_2))
        convertedHandHistoryBase = normalizePlayerNames(convertedHandHistoryBase)
        if (singleHandConverter!!.isReadYourHoleCards &&
            !nameMappingsProperties!!.keys.contains(singleHandConverter!!.yourUniqueName)
        ) {
            converterLog.warn("For hand history file {}", currentConversionFileName)
            converterLog.warn(
                "  Given yourUniqueName={} was not found with mappings "
                        + "from 'name-mappings.properties' and present player nicknames in the current hand history.",
                singleHandConverter!!.yourUniqueName
            )
            converterLog.info(
                "  To read all your hole cards you should extend 'name-mappings.properties'"
                        + " or adjust yourUniqueName to your name from 'name-mappings.properties'"
            )
        }
        var convertedHandHistory: String? = StringUtils.EMPTY
        var indexOfSingleHandBegin = 0
        var indexOfSingleHandEnd: Int =
            convertedHandHistoryBase.indexOf(PokerNowConstants.ENDING_HAND, indexOfSingleHandBegin)
        handIdPrefix = readIdPrefixFromDatetime(strippedHandHistory)
        do {
            val singleHandHistoryBase = convertedHandHistoryBase.substring(indexOfSingleHandBegin, indexOfSingleHandEnd)
            if (readSingleHand(singleHandHistoryBase)) {
                try {
                    convertedHandHistory += singleHandConverter!!.convertSingleHand(singleHandHistoryBase, handIdPrefix)
                } catch (conversionException: NumberFormatException) {
                    converterLog.error(
                        "Error converting hand {}",
                        singleHandConverter!!.readHandNumber(singleHandHistoryBase)
                    )
                    conversionErrors += singleHandHistoryBase + System.lineSeparator() + System.lineSeparator()
                } catch (conversionException: StringIndexOutOfBoundsException) {
                    converterLog.error(
                        "Error converting hand {}",
                        singleHandConverter!!.readHandNumber(singleHandHistoryBase)
                    )
                    conversionErrors += singleHandHistoryBase + System.lineSeparator() + System.lineSeparator()
                }
            }
            indexOfSingleHandBegin =
                convertedHandHistoryBase.indexOf(PokerNowConstants.STARTING_HAND, indexOfSingleHandEnd)
            indexOfSingleHandEnd =
                convertedHandHistoryBase.indexOf(PokerNowConstants.ENDING_HAND, indexOfSingleHandBegin)
        } while (indexOfSingleHandBegin > 0 && indexOfSingleHandEnd > 0)
        if (conversionErrors.isNotEmpty()) {
            val errorHandsFilename = "ErrorHands-$handIdPrefix.txt"
            FileUtils.write(File(errorHandsFilename), conversionErrors, StandardCharsets.UTF_8)
            converterLog.info("Write error file {}", errorHandsFilename)
        }
        return convertedHandHistory
    }

    private fun readSingleHand(singleHandHistoryBase: String): Boolean {
        return convertTexasHands && singleHandHistoryBase.contains(PokerNowConstants.TEXAS_GAME_TYPE) || convertOmahaHighHands && singleHandHistoryBase.contains(
            PokerNowConstants.OMAHA_GAME_TYPE
        ) || convertOmahaHighLowHands && singleHandHistoryBase.contains(PokerNowConstants.OMAHA_HI_LO_GAME_TYPE)
    }

    @Throws(IOException::class, FileNotFoundException::class)
    fun normalizePlayerNames(handHistory: String): String {
        val playerNamesById = collectNamesByPlayerId(handHistory)
        nameMappingsProperties = readNamesProperties()
        var convertedHandHistory = handHistory
        for ((key, value) in playerNamesById) {
            var mappedPlayerName = false
            for ((key1, value1) in nameMappingsProperties!!) {
                for (currentPlayerName in value) {
                    val playerNameOccurrence1: String =
                        PokerNowConstants.TRIPLE_DOUBLE_QUOTE + currentPlayerName + PokerNowConstants.PLAYER_ID_PREFIX + key + PokerNowConstants.DOUBLE_DOUBLE_QUOTE
                    val playerNameOccurrence2: String =
                        PokerNowConstants.DOUBLE_DOUBLE_QUOTE + currentPlayerName + PokerNowConstants.PLAYER_ID_PREFIX + key + PokerNowConstants.DOUBLE_DOUBLE_QUOTE
                    val usedNames = listOf(*StringUtils.split(value1.toString(), ConversionConstants.COMMA_CHAR))
                    if (usedNames.contains(currentPlayerName)) {
                        convertedHandHistory = convertedHandHistory.replace(playerNameOccurrence1, (key1 as String))
                        convertedHandHistory = convertedHandHistory.replace(playerNameOccurrence2, key1)
                        mappedPlayerName = true
                    }
                }
            }
            if (!mappedPlayerName) {
                for (currentPlayerName in value) {
                    val playerNameOccurrence1: String =
                        PokerNowConstants.TRIPLE_DOUBLE_QUOTE + currentPlayerName + PokerNowConstants.PLAYER_ID_PREFIX + key + PokerNowConstants.DOUBLE_DOUBLE_QUOTE
                    val playerNameOccurrence2: String =
                        PokerNowConstants.DOUBLE_DOUBLE_QUOTE + currentPlayerName + PokerNowConstants.PLAYER_ID_PREFIX + key + PokerNowConstants.DOUBLE_DOUBLE_QUOTE
                    convertedHandHistory = convertedHandHistory.replace(playerNameOccurrence1, currentPlayerName)
                    convertedHandHistory = convertedHandHistory.replace(playerNameOccurrence2, currentPlayerName)
                }
            }
        }
        return convertedHandHistory
    }

    @Throws(IOException::class)
    fun collectNamesByPlayerId(handHistory: String?): Map<String, MutableSet<String>> {
        var handHistoryLine = StringUtils.EMPTY
        val playerNamesByPlayerId: MutableMap<String, MutableSet<String>> =
            HashMap<String, MutableSet<String>>(ConversionConstants.SMALL_CAPACITY)
        val bufferedReader = handHistory?.let { StringReader(it) }?.let { BufferedReader(it) }
        do {
            val playerId = readPlayerId(handHistoryLine)
            if (null != playerId) {
                val playerName = readPlayerName(handHistoryLine)
                if (playerNamesByPlayerId.containsKey(playerId)) {
                    playerNamesByPlayerId[playerId]!!.add(playerName)
                } else {
                    val playerNames: MutableSet<String> = HashSet()
                    playerNames.add(playerName)
                    playerNamesByPlayerId[playerId] = playerNames
                }
            }
            handHistoryLine = bufferedReader?.readLine().toString()
        } while (handHistoryLine.isNotEmpty())
        return playerNamesByPlayerId
    }

    private fun readPlayerId(handHistoryLine: String?): String? {
        if (handHistoryLine != null) {
            if (handHistoryLine.startsWith(PokerNowConstants.TRIPLE_DOUBLE_QUOTE) && handHistoryLine.contains(
                    PokerNowConstants.PLAYER_ID_PREFIX
                )
            ) {
                val indexOfIdStart: Int =
                    handHistoryLine.indexOf(PokerNowConstants.PLAYER_ID_PREFIX) + PokerNowConstants.PLAYER_ID_PREFIX_LENGTH
                val indexOfIdEnd: Int = handHistoryLine.indexOf(PokerNowConstants.DOUBLE_QUOTE, indexOfIdStart)
                return handHistoryLine.substring(indexOfIdStart, indexOfIdEnd)
            }
        }
        return null
    }

    /**
     * There is a player name on this line assumption.
     * The player id was already read before
     *
     * @param handHistoryLine
     * @return
     */
    private fun readPlayerName(handHistoryLine: String?): String {
        val indexOfPlayerName: Int = handHistoryLine?.indexOf(PokerNowConstants.TRIPLE_DOUBLE_QUOTE) ?: -1
        val indexOfPlayerNameStart: Int = if (indexOfPlayerName >= 0) {
            indexOfPlayerName + 3
        } else {
            (handHistoryLine?.indexOf(PokerNowConstants.DOUBLE_DOUBLE_QUOTE) ?: -1) + 2
        }
        val indexOfPlayerNameEnd: Int = handHistoryLine?.indexOf(PokerNowConstants.PLAYER_ID_PREFIX) ?: -1
        return handHistoryLine!!.substring(indexOfPlayerNameStart, indexOfPlayerNameEnd)
    }

    @Throws(IOException::class)
    fun sortedHandHistoryLines(handHistory: String?): String {
        val sortedHandHistoryLines: SortedMap<Long, String?> = TreeMap()
        val reader = handHistory?.let { StringReader(it) }?.let { BufferedReader(it) }
        reader?.readLine()
        var currentLine = reader?.readLine()
        do {
            val entryKey = currentLine!!.substring(
                currentLine.lastIndexOf(
                    ConversionConstants.COMMA_CHAR
                ) + 1, currentLine.length
            )
            sortedHandHistoryLines[entryKey.toLong()] = currentLine
            currentLine = reader?.readLine()
        } while (!currentLine.isNullOrEmpty())
        var sortedHandHistory = ""
        for ((_, value) in sortedHandHistoryLines) {
            sortedHandHistory += value + ConversionConstants.POKERNOW_NEWLINE
        }
        return sortedHandHistory
    }

    companion object {
        const val IO_EXCEPTION_MESSAGE = "I/O Exception, please try again."
        const val PROPERTIES_NOT_FOUND_MESSAGE = ("Property file 'conversion.properties' not found. Make sure "
                + "you start the application from the directory containing the property files")
        private val converterLog = LogManager.getLogger(PokerNowHandHistoryConverter::class.java)

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val converter = PokerNowHandHistoryConverter()
            var configuredSourceFolder: String? = readSourceFolderProperty()
            if (StringUtils.isEmpty(configuredSourceFolder)) {
                configuredSourceFolder = System.getProperty("user.dir")
            }
            converterLog.info("Using source folder with PokerNow csv histories {}", configuredSourceFolder)
            val sourceDirectory = Paths.get(configuredSourceFolder.toString())
            val files = sourceDirectory.toFile().listFiles()
            val sourceFiles = converter.getSourceFiles(files)
            if (sourceFiles.isNotEmpty()) {
                converter.readConversionParameters()
                var configuredDestinationFolder: String? = readDestinationFolderProperty()
                if (StringUtils.isEmpty(configuredDestinationFolder)) {
                    configuredDestinationFolder = System.getProperty("user.dir")
                }
                converterLog.info(
                    "Using destination folder for converted hand histories {}",
                    configuredDestinationFolder
                )
                for (currentSourceFile in sourceFiles) {
                    val currentHandHistory = FileUtils.readFileToString(
                        currentSourceFile,
                        StandardCharsets.UTF_8
                    )
                    converter.currentConversionFileName = currentSourceFile.name
                    val sortedHandHistory = converter.sortedHandHistoryLines(currentHandHistory)
                    var convertedHandHistory =
                        converter.convertHandHistory(sortedHandHistory, converter.currentConversionFileName)
                    if (1.0 != converter.currencyFactor) {
                        convertedHandHistory = converter.replaceBetAmounts(convertedHandHistory)
                    }
                    val destinationFileName = currentSourceFile.name.replace(".csv", "-converted.txt")
                    FileUtils.write(
                        Paths.get(configuredDestinationFolder.toString(), destinationFileName).toFile(),
                        convertedHandHistory, StandardCharsets.UTF_8
                    )
                    converterLog.info(
                        "{}{}{} converted successfully to {}{}{}",
                        configuredSourceFolder, File.separator, converter.currentConversionFileName,
                        configuredDestinationFolder, File.separator, destinationFileName
                    )
                }
            } else if (!Paths.get(configuredSourceFolder.toString()).toFile().exists()) {
                converterLog.warn("Directory not found: Folder '{}' does not exist", configuredSourceFolder)
            } else {
                converterLog.warn("No csv file found: Folder '{}' contains no csv", configuredSourceFolder)
            }
        }
    }
}
