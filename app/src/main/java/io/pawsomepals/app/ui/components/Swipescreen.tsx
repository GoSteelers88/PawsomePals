import React, { useState } from 'react';
import { ChevronLeft, ChevronRight, X, Heart, Star, Camera, Cake, Zap, MapPin, User, Paw } from 'lucide-react';
import { Card } from '@/components/ui/card';

export default function DualProfileSwipeScreen() {
  const [currentUserPhotoIndex, setCurrentUserPhotoIndex] = useState(0);
  const [currentDogPhotoIndex, setCurrentDogPhotoIndex] = useState(0);
  const [activeGallery, setActiveGallery] = useState('dog'); // 'dog' or 'user'

  const mockProfile = {
    owner: {
      name: "Sarah",
      location: "2 miles away",
      photos: [
        "/api/placeholder/400/600",
        "/api/placeholder/400/600"
      ]
    },
    dog: {
      name: "Max",
      age: "2 years",
      breed: "Golden Retriever",
      energyLevel: "High Energy",
      photos: [
        "/api/placeholder/400/600",
        "/api/placeholder/400/600",
        "/api/placeholder/400/600"
      ]
    },
    compatibilityScore: 85
  };

  return (
    <div className="bg-gray-100 w-full h-screen p-4 flex items-center justify-center">
      <div className="relative w-full max-w-sm h-[600px]">
        {/* Main Card */}
        <Card className="w-full h-full overflow-hidden relative shadow-xl rounded-xl">
          <div className="relative w-full h-full">
            {/* Split View Container */}
            <div className="relative w-full h-full flex flex-col">
              {/* Main Photo Area (70% height) */}
              <div className="relative w-full h-[70%] overflow-hidden">
                <img
                  src={activeGallery === 'dog'
                    ? mockProfile.dog.photos[currentDogPhotoIndex]
                    : mockProfile.owner.photos[currentUserPhotoIndex]
                  }
                  alt="Profile"
                  className="w-full h-full object-cover"
                />

                {/* Photo Navigation Indicators */}
                <div className="absolute top-4 left-0 right-0 flex justify-center gap-2">
                  {(activeGallery === 'dog' ? mockProfile.dog.photos : mockProfile.owner.photos).map((_, index) => (
                    <div
                      key={index}
                      className={`h-1 w-8 rounded-full ${
                        index === (activeGallery === 'dog' ? currentDogPhotoIndex : currentUserPhotoIndex)
                          ? 'bg-white'
                          : 'bg-white/50'
                      }`}
                    />
                  ))}
                </div>

                {/* Gallery Toggle Buttons */}
                <div className="absolute top-4 right-4 flex gap-2">
                  <button
                    onClick={() => setActiveGallery('user')}
                    className={`p-2 rounded-full ${
                      activeGallery === 'user'
                        ? 'bg-white text-blue-500'
                        : 'bg-white/50 text-white'
                    }`}
                  >
                    <User size={20} />
                  </button>
                  <button
                    onClick={() => setActiveGallery('dog')}
                    className={`p-2 rounded-full ${
                      activeGallery === 'dog'
                        ? 'bg-white text-blue-500'
                        : 'bg-white/50 text-white'
                    }`}
                  >
                    <Paw size={20} />
                  </button>
                </div>

                {/* Photo Navigation Buttons */}
                <button
                  onClick={() => {
                    if (activeGallery === 'dog') {
                      setCurrentDogPhotoIndex(Math.max(0, currentDogPhotoIndex - 1));
                    } else {
                      setCurrentUserPhotoIndex(Math.max(0, currentUserPhotoIndex - 1));
                    }
                  }}
                  className="absolute top-1/2 left-2 -translate-y-1/2 w-8 h-8 flex items-center justify-center rounded-full bg-white/80 shadow-lg"
                >
                  <ChevronLeft className="w-5 h-5" />
                </button>
                <button
                  onClick={() => {
                    if (activeGallery === 'dog') {
                      setCurrentDogPhotoIndex(Math.min(mockProfile.dog.photos.length - 1, currentDogPhotoIndex + 1));
                    } else {
                      setCurrentUserPhotoIndex(Math.min(mockProfile.owner.photos.length - 1, currentUserPhotoIndex + 1));
                    }
                  }}
                  className="absolute top-1/2 right-2 -translate-y-1/2 w-8 h-8 flex items-center justify-center rounded-full bg-white/80 shadow-lg"
                >
                  <ChevronRight className="w-5 h-5" />
                </button>
              </div>

              {/* Thumbnail Strip (10% height) */}
              <div className="w-full h-[10%] bg-gray-900/90 flex items-center px-2">
                <div className="flex gap-2 overflow-x-auto py-2">
                  {[...mockProfile.owner.photos, ...mockProfile.dog.photos].map((photo, index) => (
                    <div
                      key={index}
                      onClick={() => {
                        if (index < mockProfile.owner.photos.length) {
                          setActiveGallery('user');
                          setCurrentUserPhotoIndex(index);
                        } else {
                          setActiveGallery('dog');
                          setCurrentDogPhotoIndex(index - mockProfile.owner.photos.length);
                        }
                      }}
                      className={`relative h-full aspect-square rounded-lg overflow-hidden cursor-pointer ${
                        (index < mockProfile.owner.photos.length ? activeGallery === 'user' && index === currentUserPhotoIndex
                          : activeGallery === 'dog' && (index - mockProfile.owner.photos.length) === currentDogPhotoIndex)
                          ? 'ring-2 ring-blue-500'
                          : ''
                      }`}
                    >
                      <img src={photo} alt="" className="w-full h-full object-cover" />
                      {index < mockProfile.owner.photos.length ? (
                        <div className="absolute top-1 right-1 bg-black/50 rounded-full p-1">
                          <User size={12} className="text-white" />
                        </div>
                      ) : (
                        <div className="absolute top-1 right-1 bg-black/50 rounded-full p-1">
                          <Paw size={12} className="text-white" />
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* Profile Info Area (20% height) */}
              <div className="relative w-full h-[20%] bg-white p-4">
                <div className="flex justify-between items-start">
                  <div>
                    <h2 className="text-xl font-bold flex items-center gap-2">
                      {mockProfile.owner.name} & {mockProfile.dog.name}
                      <div className="flex items-center gap-1 text-sm text-gray-500">
                        <MapPin size={14} />
                        <span>{mockProfile.owner.location}</span>
                      </div>
                    </h2>

                    {/* Dog Info Chips */}
                    <div className="flex flex-wrap gap-2 mt-2">
                      <div className="flex items-center gap-1 bg-gray-100 px-2 py-1 rounded-full text-sm">
                        <Camera size={14} />
                        <span>{mockProfile.dog.breed}</span>
                      </div>
                      <div className="flex items-center gap-1 bg-gray-100 px-2 py-1 rounded-full text-sm">
                        <Cake size={14} />
                        <span>{mockProfile.dog.age}</span>
                      </div>
                      <div className="flex items-center gap-1 bg-gray-100 px-2 py-1 rounded-full text-sm">
                        <Zap size={14} />
                        <span>{mockProfile.dog.energyLevel}</span>
                      </div>
                    </div>
                  </div>

                  {/* Compatibility Score */}
                  <div className="flex flex-col items-center">
                    <div className="w-12 h-12 rounded-full border-4 border-green-500 flex items-center justify-center">
                      <span className="text-lg font-bold">{mockProfile.compatibilityScore}%</span>
                    </div>
                    <span className="text-xs text-gray-500 mt-1">Match</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Card>

        {/* Action Buttons */}
        <div className="absolute -bottom-16 left-1/2 -translate-x-1/2 flex items-center gap-4">
          <button className="w-14 h-14 flex items-center justify-center rounded-full bg-white shadow-lg border-2 border-red-500">
            <X className="w-8 h-8 text-red-500" />
          </button>
          <button className="w-14 h-14 flex items-center justify-center rounded-full bg-white shadow-lg border-2 border-blue-500">
            <Star className="w-8 h-8 text-blue-500" />
          </button>
          <button className="w-14 h-14 flex items-center justify-center rounded-full bg-white shadow-lg border-2 border-green-500">
            <Heart className="w-8 h-8 text-green-500" />
          </button>
        </div>
      </div>
    </div>
  );
}