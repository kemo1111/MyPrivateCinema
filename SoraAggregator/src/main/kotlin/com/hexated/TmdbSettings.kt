package com.hexated

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The user's own TMDB v3 API key, stored only in this app's private preferences. Never compiled in. */
object TmdbKey {
    private const val PREFS = "sora_aggregator"
    private const val KEY = "tmdb_api_key"
    @Volatile private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext ?: context }

    /** A TMDB v3 API key is 32 hex characters. Format check only, no network. */
    fun isValid(value: String): Boolean = Regex("^[0-9a-fA-F]{32}$").matches(value.trim())

    fun get(): String =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, "")?.trim().orEmpty()

    fun hasKey(): Boolean = isValid(get())

    fun set(value: String): Boolean {
        val v = value.trim()
        if (!isValid(v)) return false
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, v)?.apply()
        return appContext != null
    }

    /** Throws a readable error when no valid key is saved, instead of calling TMDB with an empty key. */
    fun require() {
        if (!hasKey()) throw com.lagradost.cloudstream3.ErrorLoadingException(
            "Add your TMDB API key in the SoraAggregator plugin settings, then restart CloudStream."
        )
    }

    fun clear() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply()
    }
}

object TmdbSettingsDialog {
    const val ATTRIBUTION = "This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB."
    private const val LOGO_PNG = "iVBORw0KGgoAAAANSUhEUgAAAZAAAAA1CAYAAABvGb0lAAAS4klEQVR42u2deXRU133HP3dGQmwBjCTCIiBCCIsGxGJjl9qsBsuiJY1Tpz1ZGifd4p42bRNap27iLO5Jm7ZO26RtGrvHPSe1fer4ZHGjRELI2BgbsxmEjcACSayS2DcbhKSR5vaP3xtp0DJz39PMPI3mfs7hMEczb+a9O2/u9/7Wq+7ZulszCIM+AWiUh2MiqNjH6VsedgB/ADwHsHfdMky44+W3ABYBPwFV5O78olGg+ZGGh4GOA+uXGn1+PBbW1EYe5gE/BVbEGtdbzk8P+NxboDYCZw/fv8j4PEq2vINCK436PPA78cbp1r+rW55wnutGvrM24AZwE2jVcBg4DVwCzoG6AejGsgUJGU+vzNp8OPLwTuBrwIRbh9rg+xjwORUGupzrl3HQXNNwBGgAzgItwPuAbi0v8XUcvJBb2QigNOQChcAdwGJgKjALKNAwERjV/2jVf/z0gA/7juug55OU+UrLuZoeN+jvw+SzBjjO/RipyHNh5N46D5wEzgBNGt4EjgKtQAggvHEmXsnyfGRmsRiYjQx8orkdmOfnxWWpMCEdnA2sTtJHhJBJtB04D+ow0ATsKaquexdodp7TTf4JygTg15DJMFloRFhvAleBWmAX8Mr0qvojzt8ZzmKSW9kQeTgOmA+sV7BSQykwCRjr9zlaAAgg4j0RKHb+FgKuAceR+24LsCdQcfoCoL0IiRUQM2Yjk8vRxTX7SZQVomT1thb4oN8XmGSykckFZHVaSu9kehp4C3gV2FZUXXcKWbnjo5gkC4VMvOMQy3Mu8CBiiVRp+B9g37Sq+k6AM8NMSBzxGI/8Fj4Jeh2oachkZRn+ZCP3XR6wDPGqvAW8COqXqqK5RYEObywwfkMrIGaMBtYBP8JZJQ6FiPvKMf3v8/vifCIymZY4/x4C3gWqgB8DdUXVdSEYkUISTRayQHkEeAD4AahnQF+cVnWEM+W3+31+5FY2oCBLi4vq88BvAPl+n5dlyExAFrD3AJ8AntbwS1XRfA1AGwiJXTmY86vAnAS/5yJgRM+OLhgNLAH+GngBeExDkQY1p7rO73NLFR9C4jDfBjXD75PJq2wgT8QjF/hT4Fngs1jxGGnkAKuA7wPfQzwEAVXRHPdAKyDmFAD3Aiyu2Z+I9wsAa0iuzz0dCSAxoceBHwIfBcbOqa4jQ4RkLOJaeByYNK3qiC8nkVfZQHXJj0FidP8CfAvxpauhvK9lWDMR+DTwDOJazY4nIlZAzMlB3E3jE/R+UxDz0TIwWYhp/QPgMTJr1ZuF/JAfBgKpFpE8iXWosvqH7kFWpZ/EBsczhQCSkfhd4PeBsbFExAqIO5YhgU/PRKXvLgV+xe8LSgOmAJuAbwOFGWKFgMSH/pAUZ+iJy0orZHHzH87/Qb8Hw5JyZgBPILG5QUXECog7ZgArYchurCCwHjEZLfEZg6zG/xnU7DnVh/w+n1RRAvwmQCqsEMfyQKOWA08iMTpL5pIP/A3wexpGMYCIWAFxRzbixhrqxF+AUzhoMSaIZP98DZiSISISRCyAycn+oLze+o5iZOW52O+LtwwLcoGvAB8HAn1FJAuo8PjGE4C7kewZE04Cxzx8Tgg4l4qRMmQJUkC1y+2BpTW1kerRO5DgpMUdWcCnkMr2bxVWH2o/XvZhv87lJWA7gweVA0i8rBiYj2YO3hYeC4CZwOVkXoxzEZO1xJvWJPOzLGnHVCSp4xiwk4pmcFJ8I8E6t4QRH/4LwDTDY14Gvoy0unBLW4oHLBbTgRVBpXd5KSpUkK3FfZWoYLwf3ACeBhoZeAINIm6nfMSHP80Zt6kM3erNQWoR9iv0zwqrD+GTiLyuJDuJ0w/Mv+WJGZvrIw8DyAJrErLw+AzwEcwXXSDB6wLg7WRdSL5YH0GkFuC3sZ4JS39uBx5FYiI9C/qsHffd/Z6bd1m+dU/kYRux27v0pRO4pqBr131m/ax8oBMR1Vg/oCCwtlurZ/C2KpwFLPf7QodIB/Bz0NsAjt5f2vPE3OqDEUVRyFjlIJPgHGQ1vRpx383Ce0poPrBJow4gbRn8YNB7pOWBngryMNA2o6q+TUOrgp3ACeCLiDvUhNGI+CaF/F7X1SKk1mNcUkfNks6UI4uM71HRHGZjQUor0dMhf7wOmdjy4rxuIZJB9YbpG0fcVwqWKZhroLwnkMnX1MJLJYpBJtDGsoWRhxppSdIF3JhbffCCgj0annfG7mNIemihx3NYhqyWnyysPtTtgxWiFDpmc78ILU5LkulV9ZeRQq27kcItE4K4s1i8MBZpWDq8eqdYhhs5SGbgy0AdFc3WVO1DCzJxx2MqsEJDwE02lpIvYB1mq7wDzvmMCBrLFtJQtlA3li1sB/YD30C6/z6PWDRuGYXEQ/ya9AJZyo0BLijpilqDWCcmhBHLOOFEWR934WR7WSxxmI9YIdlgfZ19uYC0O45HEFijDLNjSntrPwoxc19dR5oLJmXi8JvGsgVo6NKwF/gz4G+RsXfLfMSsDhSmPitL3QwHXVvVTpvtY5h/tyHgSvIuQ49BhDxpbjLLiEIh3oNisALSly7gNcx+sKU4faxcWCHLMXPZNCJdMkcsTWULIk0SLwPfQUTEbUwpC2lAGM/lmAxUwLtTNgdzl+5NpN19QsmvPIoTwrwdGUOLxZRioBwdUFZAbkUhcZBGg9fmA6u0eZXuOCSnf4zBa/cik8aI/34cEWkH/hvJ7HJrdS1CxJwUWyFKucoh6SHLOd8cw9e3kiRXZkChkLom7zsKWTKRIFCOCk8Z8ROUSwLIj9WkxiMArFJxejRFua/mYOa+agNeIQFt49MFR0RuIK0zXnV5+GSkU3Kq72VX9sf0qp7U3hIkDmbKNpIkIGHNJOdcbKsSi1tKgVIrIP0JIZPYdYPXliIZWSyK78ZageTzx6MJsUAU6ZG5lkiagadw58oKIBlZqU4/Nf7tzHDEQ0mG318CpiljZ5DtjrsSuS+IuK8A2YfEbidg8cJk4D67oVR/FJIB1UT8XkCTgbVaBKcrxusmIPUPJm6L3cgufbf5PRCppKlsAUXSKPE1pF7i110cXoT0Kat3ccxQCQBBDbpg87v9hF6cW0ohLqs8pVmOdDddjZn4dCMZansMXuuVpUhGocXiliBwlxWQ/gSQCXw38QVEaWmuOJXYgc65yCo5Hm0KvU2jOsk86yPCZWSv5vVIqq4JM5EsovoUVqavJmaLc6WADyAB/gIU89HGrUw0kmv/n0B7UnYlVATQFGF3JbV4Z5m9eQamC9gK/C7xg94fRlxZzYtq9vN2VGuTqOJBU/fVCUS4MpIoK+RNJK3XdFe+yF7PqWQV8YsBvSwCNHLv/RXeeseZfspYJA3aYvHKeBsDGZx9mP2AJyKByAHFWEkfpLWYrfR2koSUzTTkDJJ9ZEo2UKBTa7Upg39uuQH8L/AFhT4IJHxP9PyqnvjHGLx3AbBYgAxIEx0Cp0GZ1mLci1OINUAwvQTxNcejDcm4aff7wocBV4CjLl6fBRSo9L2fbyKxji8BXwBdr0m8eADR3etyMHcRWiwDYl1Yg6I6ET/0J4j/QytBuq2eivyhtKYWhVYatQKzKt9TiAVikdYmV10eMwkREC/dnv2+1peQeMceBR2t5SnpzpKD/f1bhoi9gWKzF+n2Gm8p+AHEjVVFVCGcRt2GBINNVsY7sO6rCGFiZ7UNRLrWMmQD9yPptK9qeGla1ZE9iDsrOVZI7+em65hZhgnpavKnipNILMSElfR3Yy3EbFvQm4j7yktTwZHIoN1+Y5BulkeEALLr20KkL9gLwL8ibs9AErey7cK8oaPFMiBWQGKhaUcyYkIGry5CupoCCscfvwqYYnBsj/vq4Polfl/1cCCyj4gb3sfd/jTDlSlIvchzwEeB7CSJSAfurTyL5RasgAyGk38L7NZSFxKPcUhfoRzn8DzEfWXCDsw+I1MYj3kKL8i3NVIEBOTOm4/sePgQEEyYiPTmhoVIX6vNMkywAjIYCpmONCeQ/StMWAnMdOaxRZi1rLiJ9L4aka3bPXIbYtGZ0gWcCaJH2oQ4C+lSvBZgagJEJCq3uB1vLfQtlh5sEH0QatctZUnNfrQEM18BHiR+0LEQWH6xe2xTXvDmGm3WjuQkGVw8OAizkbiAKZ3Aqe7UFu83IfuUx7J6Itv6jkdTgOwuOR53C7ci4FENh0lAU8Xz5fMivbDakPdck8pBs4wotBWQGETNDDuRH++sOIfkAOvygm1vgFpt+DFv4qT/Znr8o6i6Do1CoZfjTkDacdJ+U7i17RbgUeLHEQJIy5OpyAJjDbAB2VPBVEjuBR7MVuF/n1p1hLOJyczqxGz3TYtlMBqtgMTgwPqlkc2ijgG1xBcQkNbiH0P2/Y5HO1JrYt1XDgqdj3nDwQiNpH4yDAHtCrpOPxC3I0jb9Kr6i8heM9VIk8QvAR/H2Ro0DqOBB0M68CJwPoHX8DZStJlRjTstCWOXjYGY8R7SJdYkSFsI/DlSGxKP44zwnQdNcXpggay273R5+GFS7893lQLbWl6CUyDYiaSGP4aIiSmmKeFuqMfWHlm80QbssAJizg7grMHrspHusCZjuwvrvormg8DncLci7kS+m1S3gPFUQxFVZX4K+CFwzfDQXJydF4caTL+wYV7k4Tlge3KHyTJCOQG8bgUkDgd6u+s2IvuEJIoOoAZbPBixPrKBz+Butz6QG3kvpDT+AZEcPQ9EicgBotrfxCGA7Grptj4mFp1ILOe9pIyQZSTzOtBkBcQQpfQV4I0EvmUjjvsqk60PRzyCSL3DX2C2Z3w023DXeDFRJKKKuw13llMRMfcgcUFvwtpuzLstWCwgcbMKoMMKiCFaK42Y+4kKYu7BfPU5InHEIwdpWPkPmDWdjOYy8Av86WCcCAGZiKT1unl9QhJfLpTPAxQhHTyHtE/JeEvYYsx2JHvUFhKaEOXGOgK8k4C37ABqVIb+aOdW1zG3ui6g4EPA14HvIHEjt1QjpnSq3VcwBAGZXtWz8+5SzDL7IoRIaLW9JluFATZjkzksZlxF2uxcAVtI6JZLiPK69dP3pRHYp8kc99Xc6oMo2QJ4FDJpliM7Pi7G233YCjyD+7bvicJzDMRhOvAppAWOKZcw68tmxIUN88ivbKALdSoL/TQSpDfJHrRkLr9AYrewscAKiEvCiM/9T3BX6NaX3aS3+2rQybO4+iD0dtMdhdQwjNdQqGXflLuAe5B94r3efyEkg+kN8MX6AA8WSJTlMR34Mk6LEhccwWnznigubCgmv7IBxKe9Hvh0ogfKMmI4huxb05M5aAXEkKiiwnrgIFLs5oV2YKuC9jTu/Dca+C1QSwA1b8tB0D2KEkDuq/FI9XUxkpY7BWkwmQi36XbgKaDDJ/EAEZCABgo2v9vvSX1rW5UgmjHAbRruRrrtrsbdjoAdSPuUpHTQ1XBFSfPGRUjNicUSzXXk/pC2SxsLACsgXjiPtDZZ7fH4JtI/+2osYoX5QR3wDQUnfRbgFcA3DV+bg/T3mo+477y4iZqRxItEtTLp4cKGYvLECqkF/g6ZKKYmY9AsaUk38CLSQaE7Ih5gBcQL3UhV+iN4awGxC9u63SstwNc17FD45rqKsAY3jQgj3Z29sx2JnSWFiyIiGtledybwODYeYhFL++dIV+gr0eIBNgvLFVHZWIeQ9hluiew8eNPva0lDTgJfASoU6GP+ikeqaUFWfzcSbX1Ec3FDMYiL9SnE123v08wmjATMv6oG6TVnBcQb5/DWgv04jhvinfR1X/nBQeCLwPOgQxkmHiHgWRJbxDoojoi8B/w98E846ZqWjKMbybjaRGSx3Mf6ACsgXgkhbizTPkYRdmLdV24IIbUejyBmdNexsgV+n1Mq6QZ+Avybgo5kWh/ROCJyFRGQryLWnyVzaKO3Y/QhAD2AeIAVENdEubHewV0LjTZEdKxbwIwW4Engj5Dam+4MFI+fIm671jMpEo8IjohcB/4L+GOkYHOk7fho6U8LkhyyCWjSGwsGFQ+wAjIUWnHcUYacwHF7WfdVTK4j1sZngSeUUy+TYeJxBYlDbEJy733BEZEQUqn+OeAfkfveMvJoA/4P+d19F7hocpDNwvKIhk4lmTEPY9bPyLqvYnMV8fM/B2wFLiqgKbOEoxPpLPx9RESvp9ry6IsjIjq3sqEJeALp3vswsqviFF9PzpII2pCF8LNAhUJf0KiYVkc0VkA8EFVUuA+p64i30c8NHPeVtT560EgM6TjwKrLK3delA1eyVFhnkHCEEYujFvgZIhwtgPZbPKK55GRo5VY2bENE7k7gI0g1/VzcNYW0+EsnsrfRm0ig/BXgrELr8EZ3LemsgHhEAwrOILGQeAJyFtsyO0IIGbM9iKVRi1hmoQwSDZAfcS3i1tyCFJeeV6Bbh5Fw9OXShmJyKxtugHoNmYBma2lNsxLZzrkE6xofrlxENl/biZQTHAbeBwgbWhx9+X80rwrqmHLX0AAAAABJRU5ErkJggg=="

    fun build(context: Context): AlertDialog {
        val pad = (context.resources.displayMetrics.density * 16).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(s: String, size: Float = 14f, color: Int = Color.DKGRAY) = TextView(context).apply {
            this.text = s; textSize = size; setTextColor(color)
            setPadding(0, pad / 3, 0, pad / 3)
        }
        root.addView(text("TMDB API key (v3)", 16f, Color.BLACK))
        root.addView(text("Enter your own free TMDB API key (32 characters). It is saved only on this device and is used to load titles and posters."))
        val input = EditText(context).apply {
            setSingleLine(true)
            hint = "32-character v3 API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            tag = "tmdb_key_input"
        }
        root.addView(input)
        val status = text("", 13f).apply { tag = "tmdb_key_status" }
        fun refresh() {
            status.text = if (TmdbKey.hasKey()) "A key is saved. Restart CloudStream after changing it." else "No key saved. Titles will not load."
        }
        refresh()
        root.addView(status)
        val bytes = Base64.decode(LOGO_PNG, Base64.DEFAULT)
        val logo = ImageView(context).apply {
            setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            adjustViewBounds = true
            maxHeight = (context.resources.displayMetrics.density * 20).toInt()
            tag = "tmdb_logo"
            contentDescription = "TMDB"
        }
        root.addView(logo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = pad })
        root.addView(text(ATTRIBUTION, 12f).apply { tag = "tmdb_attribution" })

        val scroll = ScrollView(context).apply { addView(root) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("SoraAggregator")
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNeutralButton("Clear", null)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = input.text.toString()
                if (TmdbKey.set(v)) { input.setText(""); refresh() }
                else status.text = "That is not a valid v3 key (32 letters or digits). Nothing was saved."
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                TmdbKey.clear(); input.setText(""); refresh()
            }
        }
        return dialog
    }
}
