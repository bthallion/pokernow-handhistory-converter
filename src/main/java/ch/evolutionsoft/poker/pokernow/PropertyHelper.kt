package ch.evolutionsoft.poker.pokernow

import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

object PropertyHelper {
    const val SOURCE_FOLDER_PROPERTY = "folderWithLatestCsv"
    const val DESTINATION_FOLDER_PROPERTY = "folderOfConvertedCsv"
    const val CURRENCY_FACTOR = "currencyReductionFactor"
    const val CONVERT_OMAHA_HIGH_HANDS = "convertOmahaHiHands"
    const val CONVERT_OMAHA_HIGH_LOW_HANDS = "convertOmahaHiLoHands"
    const val CONVERT_TEXAS_HANDS = "convertTexasHands"
    const val READ_YOUR_HOLE_CARDS = "readYourHoleCards"
    const val YOUR_UNIQUE_NAME = "yourUniqueName"
    @JvmStatic
    @Throws(IOException::class)
    fun readNamesProperties(): Properties {
        val nameMappingsProperties = Properties()
        nameMappingsProperties.load(FileInputStream("./name-mappings.properties"))
        return nameMappingsProperties
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readSourceFolderProperty(): String {
        val conversionProperties = readConversionProperties()
        return conversionProperties.getProperty(SOURCE_FOLDER_PROPERTY)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readDestinationFolderProperty(): String {
        val conversionProperties = readConversionProperties()
        return conversionProperties.getProperty(DESTINATION_FOLDER_PROPERTY)
    }

    @JvmStatic
    @Throws(FileNotFoundException::class, IOException::class)
    fun readCurrencyFactor(): Double {
        val conversionProperties = readConversionProperties()
        return conversionProperties.getProperty(CURRENCY_FACTOR).toDouble()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readConvertOmahaHighHands(): Boolean {
        val conversionProperties = readConversionProperties()
        return java.lang.Boolean.parseBoolean(conversionProperties.getProperty(CONVERT_OMAHA_HIGH_HANDS))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readConvertOmahaHighLowHands(): Boolean {
        val conversionProperties = readConversionProperties()
        return java.lang.Boolean.parseBoolean(conversionProperties.getProperty(CONVERT_OMAHA_HIGH_HANDS))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readConvertTexasHands(): Boolean {
        val conversionProperties = readConversionProperties()
        return java.lang.Boolean.parseBoolean(conversionProperties.getProperty(CONVERT_TEXAS_HANDS))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readConvertYourHoleCards(): Boolean {
        val conversionProperties = readConversionProperties()
        return java.lang.Boolean.parseBoolean(conversionProperties.getProperty(READ_YOUR_HOLE_CARDS))
    }

    @JvmStatic
    @Throws(FileNotFoundException::class, IOException::class)
    fun readYourUniqueName(): String {
        val conversionProperties = readConversionProperties()
        return conversionProperties.getProperty(YOUR_UNIQUE_NAME)
    }

    @Throws(IOException::class)
    fun readConversionProperties(): Properties {
        val conversionProperties = Properties()
        conversionProperties.load(FileInputStream("./conversion.properties"))
        return conversionProperties
    }
}
