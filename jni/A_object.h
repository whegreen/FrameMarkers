/*==============================================================================
Copyright (c) 2010-2013 QUALCOMM Austria Research Center GmbH.
All Rights Reserved.

@file 
    A_object.h

@brief
    Geometry for the A object used in the samples.

==============================================================================*/
#ifndef _A_OBJECT_H_
#define _A_OBJECT_H_


#define NUM_A_OBJECT_VERTEX 4
#define NUM_A_OBJECT_INDEX 6


static const float AobjectVertices[NUM_A_OBJECT_VERTEX * 3] =
{
//		-1.00f,  -1.00f,   0.00f, // front
//		 1.00f,  -1.00f,   0.00f,
//		 1.00f,   1.00f,   0.00f,
//		-1.00f,   1.00f,   0.00f

		0.00f,  0.00f,   0.00f, // front
		 2.00f,  0.00f,   0.00f,
		 2.00f,   2.00f,   0.00f,
		0.00f,   2.00f,   0.00f
};

static const float AobjectTexCoords[NUM_A_OBJECT_VERTEX * 2] =
{
		0, 0,
		1, 0,
		1, 1,
		0, 1
//		-0.5, -0.5,
//		0.5, -0.5,
//		0.5, 0.5,
//		-0.5, 0.5
};

static const float AobjectNormals[NUM_A_OBJECT_VERTEX * 3] =
{
		0, 0, 1,
		0, 0, 1,
		0, 0, 1,
		0, 0, 1
};

static const unsigned short AobjectIndices[NUM_A_OBJECT_INDEX] =
{
		0,  1,  2,  0,  2,  3, // front
};


#endif // _A_OBJECT_H_
