import java.io.File
import org.json.JSONObject

enum class MigrationMaturityState(val percentage: Int, val description: String) {
    EXISTS(15, "Subsystem exists in codebase."),
    COMPILES(30, "Subsystem compiles without errors."),
    TESTED(50, "Subsystem is unit and integration tested."),
    WIRED_DEV(70, "Subsystem is wired in developer debug runtime."),
    WIRED_PROD(85, "Subsystem is wired in production runtime (e.g. parallel validation)."),
    VALIDATED(90, "Subsystem is validated via packaging validation suite."),
    VERIFIED_PROD(100, "Subsystem is fully verified in production and stable.")
}

object MigrationStatusFramework {
    fun getPercentage(state: MigrationMaturityState): Int {
        return state.percentage
    }

    fun getMaturityState(percentageString: String): MigrationMaturityState {
        return try {
            MigrationMaturityState.valueOf(percentageString.trim().uppercase())
        } catch (e: Exception) {
            MigrationMaturityState.EXISTS
        }
    }
}
