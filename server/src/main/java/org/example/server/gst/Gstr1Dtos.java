package org.example.server.gst;

import java.math.BigDecimal;
import java.util.List;

public final class Gstr1Dtos {
    private Gstr1Dtos() {}

    public record Gstr1ItemDetailDto(
            int num,
            BigDecimal txval,
            BigDecimal rt,
            BigDecimal iamt,
            BigDecimal camt,
            BigDecimal samt,
            BigDecimal csamt
    ) {}

    public record Gstr1B2bInvoiceDto(
            String inum,
            String idt,
            BigDecimal val,
            String pos,
            String rchrg,
            String inv_typ,
            List<Gstr1ItemDetailDto> itms
    ) {}

    public record Gstr1B2bPartyDto(
            String ctin,
            String partyName,
            List<Gstr1B2bInvoiceDto> inv
    ) {}

    public record Gstr1B2csDto(
            String sply_ty,
            String pos,
            BigDecimal txval,
            BigDecimal rt,
            BigDecimal iamt,
            BigDecimal camt,
            BigDecimal samt,
            BigDecimal csamt
    ) {}

    public record Gstr1CdnrNoteDto(
            String nt_num,
            String nt_dt,
            String ntty,
            String inum,
            String idt,
            BigDecimal val,
            String pos,
            String rchrg,
            List<Gstr1ItemDetailDto> itms
    ) {}

    public record Gstr1CdnrPartyDto(
            String ctin,
            String partyName,
            List<Gstr1CdnrNoteDto> nt
    ) {}

    public record Gstr1HsnItemDto(
            int num,
            String hsn_sc,
            String desc,
            String uqc,
            BigDecimal qty,
            BigDecimal val,
            BigDecimal txval,
            BigDecimal rt,
            BigDecimal iamt,
            BigDecimal camt,
            BigDecimal samt,
            BigDecimal csamt
    ) {}

    public record Gstr1DocSummaryDto(
            int doc_num,
            String doc_name,
            String from_serial,
            String to_serial,
            long total_count,
            long cancelled_count,
            long net_issued_count
    ) {}

    public record Gstr1FullReturnDto(
            String gstin,
            String fp,
            String version,
            BigDecimal grossTurnover,
            BigDecimal totalTaxable,
            BigDecimal totalIgst,
            BigDecimal totalCgst,
            BigDecimal totalSgst,
            BigDecimal totalTax,
            List<Gstr1B2bPartyDto> b2b,
            List<Gstr1B2csDto> b2cs,
            List<Gstr1CdnrPartyDto> cdnr,
            List<Gstr1HsnItemDto> hsn,
            List<Gstr1DocSummaryDto> documents
    ) {}
}
