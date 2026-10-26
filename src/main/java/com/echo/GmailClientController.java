package com.echo;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gmail Client (Story 5569) at /api/gmail-client. Every endpoint lives in {@link GmailClientEndpoints}.
 *
 * Story 5654: echoed into Echo as the Email Client, over Outlook. Leonardo's controller also read
 * Reading Nook files and saved attachments to Leonardo Files; Echo has neither, so this controller
 * only hands the endpoints their service and the whitelist / blacklist.
 */
@RestController
@RequestMapping("/api/gmail-client")
public class GmailClientController extends GmailClientEndpoints {

    private final GmailClientService service;
    private final MailTriageService triage;

    public GmailClientController(GmailClientService service, MailTriageService triage) {
        this.service = service;
        this.triage = triage;
    }

    @Override
    protected GmailClientService service() { return service; }

    @Override
    protected MailTriageService triage() { return triage; }
}
