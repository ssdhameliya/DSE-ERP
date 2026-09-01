#!/usr/bin/env python3
from pathlib import Path
import re,sys,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def t(p): return (R/p).read_text(encoding='utf-8')
def need(ok,msg):
    if not ok: print('FAIL:',msg);sys.exit(1)
need('<version>9.0.56</version>' in t('pom.xml') and '<dse.phase>9.0.56</dse.phase>' in t('pom.xml'),'root version')
need('APP_VERSION = "9.0.56"' in t('shared/src/main/java/org/example/shared/RuntimeContract.java'),'runtime version')
need('version=9.0.56' in t('desktop/src/main/resources/app-version.properties'),'desktop version')
need('dse.app.version=9.0.56' in t('server/src/main/resources/application.properties'),'server version')
fx=t('desktop/src/main/resources/fxml/pages/Customer360.fxml'); ctl=t('desktop/src/main/java/org/example/controller/Customer360Controller.java')
ET.parse(R/'desktop/src/main/resources/fxml/pages/Customer360.fxml')
for label in ('Overview','Contacts','Quotations','Sales Orders','Projects','Invoices','Payments','Notes','Documents'):
    need(('text="'+label+'"') in fx,'missing Customer 360 tab '+label)
for action in ('Back to Customers','Edit Customer','New Sale'):
    need(('text="'+action+'"') in fx,'missing header action '+action)
need('New Quotation' not in fx,'Customer 360 header must not expose New Quotation')
need('CustomerSaleContext.select(customer.getId())' in ctl and '"/fxml/pages/Sale.fxml"' in ctl,'New Sale must reuse existing Sale screen')
need('Customer360Context.select(party)' in t('desktop/src/main/java/org/example/controller/CustomerController.java'),'Customer register entry point')
need('360° View' in t('desktop/src/main/resources/fxml/pages/Customer.fxml'),'Customer register 360 button')
server=t('server/src/main/java/org/example/server/customer360/Customer360Service.java')
for authority in ('quotation_header','workflow_document','sales_header','payment_record','party_contact','party_note'):
    need(authority in server,'missing server authority '+authority)
need('CUSTOMERS.EDIT' in server and 'row_version' in server,'multi-user contact/note protection')
support=t('server/src/main/java/org/example/server/support/SupportService.java')
need('"CUSTOMER"' in support and '"party_master"' in support,'customer attachment integration')
mig=t('server/src/main/resources/db/migration/V9_0_52__customer_360.sql')
need('CREATE TABLE IF NOT EXISTS party_contact' in mig and 'CREATE TABLE IF NOT EXISTS party_note' in mig,'customer 360 migration')
need('ALTER TABLE party_master ADD COLUMN IF NOT EXISTS attachment_path' in mig,'customer attachment column')
sales=t('desktop/src/main/java/org/example/controller/SalesController.java')
need('CustomerSaleContext.consume()' in sales and 'create-sale-360-customer-lookup' in sales,'Sale preselection handoff')
need(len(list((R/'desktop/src/main/resources/fxml').rglob('*.fxml')))==65,'expected 65 FXML after shared WorkflowEditor shell')
need(sorted(p.name for p in (R/'desktop/src/main/resources/css').glob('*.css'))==['dark-theme.css','light-theme.css'],'exactly two themes')
print('CUSTOMER_360_9_0_53_OK fxml=65 tabs=9 actions=Back/Edit/NewSale persistence=server-owned')
