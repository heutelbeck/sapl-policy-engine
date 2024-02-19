CREATE TABLE IF NOT EXISTS Patient
(
	id			    BIGINT AUTO_INCREMENT PRIMARY KEY,
	first_name	    NVARCHAR (256),
   	last_name	    NVARCHAR (256),
   	diagnosis	    NVARCHAR (256)
);
