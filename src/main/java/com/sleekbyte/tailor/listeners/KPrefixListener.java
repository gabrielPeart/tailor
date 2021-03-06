package com.sleekbyte.tailor.listeners;

import com.sleekbyte.tailor.antlr.SwiftBaseListener;
import com.sleekbyte.tailor.antlr.SwiftParser;
import com.sleekbyte.tailor.common.Location;
import com.sleekbyte.tailor.common.Messages;
import com.sleekbyte.tailor.common.Rules;
import com.sleekbyte.tailor.output.Printer;
import com.sleekbyte.tailor.utils.CharFormatUtil;
import com.sleekbyte.tailor.utils.ListenerUtil;

/**
 * Parse tree listener for 'k' prefix style check.
 */
public class KPrefixListener extends SwiftBaseListener {

    private Printer printer;

    public KPrefixListener(Printer printer) {
        this.printer = printer;
    }

    @Override
    public void enterIdentifier(SwiftParser.IdentifierContext ctx) {
        String constantName = ctx.getText();
        Location location = ListenerUtil.getContextStartLocation(ctx);
        if (CharFormatUtil.isKPrefixed(constantName)) {
            printer.warn(Rules.CONSTANT_K_PREFIX, Messages.CONSTANT + Messages.NAME + Messages.K_PREFIXED, location);
        }
    }

}
