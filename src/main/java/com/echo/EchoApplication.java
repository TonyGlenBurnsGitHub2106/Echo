package com.echo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Echo (Story 5653) -- parts of Leonardo, standing on their own: its own repository, its own
 * database (Echo), its own server. It shares no code, no database and no path with Leonardo at
 * run time; the parts it echoes were COPIED (class for class, proc for proc) so a later change
 * on either side can be carried across by comparing files with the same name.
 *
 * The first part is Solutions. The shell around it -- the icon band, Quick Launch and its
 * Arrange, the dashboard menu, the Print menu, remembered view state -- is Leonardo's own,
 * copied, so Echo looks and feels like Leonardo.
 */
@SpringBootApplication
public class EchoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoApplication.class, args);
    }
}
