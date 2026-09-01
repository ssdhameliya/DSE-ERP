package org.example.server.persistence.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="workflow_document_line", uniqueConstraints=@UniqueConstraint(columnNames={"document_id","line_no"}))
public class WorkflowDocumentLineEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id; @Column(name="document_id",nullable=false) private Integer documentId; @Column(name="line_no",nullable=false) private Integer lineNo;
 @Column(name="item_code") private String itemCode; @Column(nullable=false) private String description; @Column(nullable=false,precision=18,scale=4) private BigDecimal quantity=BigDecimal.ZERO; @Column(nullable=false,precision=18,scale=4) private BigDecimal rate=BigDecimal.ZERO; @Column(nullable=false,precision=18,scale=2) private BigDecimal amount=BigDecimal.ZERO;
 public Integer getId(){return id;} public void setId(Integer v){id=v;} public Integer getDocumentId(){return documentId;} public void setDocumentId(Integer v){documentId=v;} public Integer getLineNo(){return lineNo;} public void setLineNo(Integer v){lineNo=v;} public String getItemCode(){return itemCode;} public void setItemCode(String v){itemCode=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;} public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;} public BigDecimal getRate(){return rate;} public void setRate(BigDecimal v){rate=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
}
