
module tmengine {

	exports com.maxprograms.tmengine;

	opens com.maxprograms.tmengine to mapdb;

	requires mapdb;
	requires java.xml;
	requires java.base;
	requires java.sql;
	requires transitive openxliff;
}