package br.com.devsource.rfid.all.factory;

import br.com.devsource.rfid.api.Reader;
import br.com.devsource.rfid.api.ReaderConf;

/**
 * @author guilherme.pacheco
 */
@FunctionalInterface
public interface ReaderBuilder {

  Reader reader(ReaderConf readerConf);

}
